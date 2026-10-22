"""Public fixed date/clock fixtures and fake-turn effects only. No phone/model/network."""
import csv
from datetime import date, datetime
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'backend'))
from agenda_requests import BOGOTA, calendar_due, current_event, request
from agenda_dialogue import AgendaDialogue, hour_question, INVALID_TIME, WHEN
from assistant_tools_trial import AssistantToolsTrial
import test_assistant_tools as tools_fixture
from test_assistant_tools import native

WALL = 1792610130000


def turn(sequence, text, wall=None):
    boot = 100000 + (sequence - 1) * 3000
    return dict(id=f'{sequence:032x}', seq=sequence, text=text, speech_end=boot-100,
        calendar_boot_ms=boot, calendar_anchor_ms=WALL+(sequence-1)*3000 if wall is None else wall)


class CalendarDatesTests(unittest.TestCase):
    def answer(self, draft, human):
        return draft.answer(human, human['calendar_boot_ms'])

    def heard(self, draft, human):
        draft.spoken(human['id'], True, human['calendar_boot_ms']+300)

    def test_shared_android_python_calendar_values(self):
        with (ROOT/'tests/calendar_date_cases.tsv').open(encoding='utf-8', newline='') as stream:
            for row in csv.DictReader(stream, delimiter='\t'):
                with self.subTest(case=row['case']):
                    if row['due_ms'] == 'INVALID':
                        with self.assertRaises(ValueError): calendar_due(row['when_text'], int(row['anchor_ms']))
                    else:
                        self.assertEqual(calendar_due(row['when_text'], int(row['anchor_ms'])),
                            (int(row['due_ms']), int(row['delay_ms'])))

    def test_whole_current_request_subject_and_date_are_not_rewritten(self):
        for human in ('recuérdame regar el jardín para el dos de noviembre del 2026 a las 5pm',
                'recuérdame el dos de noviembre del 2026 a las 5pm que regar el jardín',
                'crea un recordatorio para el dos de noviembre del 2026 a las 5pm para regar el jardín'):
            value = current_event('REMINDER', human, WALL, True)
            self.assertEqual(value['text'], 'regar el jardín'); self.assertEqual(value['due_ms'],1793656800000)
            with self.assertRaises(ValueError):
                request('REMINDER', human, value['when_text'].replace('5pm','6pm'), value['text'], WALL, True)
        for human in ('recuérdame regar el jardín en tres días a las 5pm',
                'recuérdame regar el jardín dentro de tres días a las 5pm'):
            value = current_event('REMINDER', human, WALL, True)
            self.assertEqual(value['text'], 'regar el jardín'); self.assertEqual(value['due_ms'],1792879200000)

    def test_meridiem_notation_generalizes_all_twelve_hours_and_minute_boundaries(self):
        for suffixes,offset in ((('am','a. m.','a m','a eme'),0),(('pm','p. m.','p m','pe eme'),12)):
            for hour in range(1,13):
                for minute in (0,15,30,59):
                    expected=int(datetime(2026,11,2,hour%12+offset,minute,tzinfo=BOGOTA).timestamp()*1000)
                    for suffix in suffixes:
                        with self.subTest(hour=hour,minute=minute,suffix=suffix):
                            self.assertEqual(calendar_due(f'el dos de noviembre del 2026 a las {hour}:{minute:02d} {suffix}',WALL),(expected,0))

    def test_today_alarm_clock_and_today_countdown_are_distinct_and_explicit(self):
        today = datetime.fromtimestamp(WALL / 1000, BOGOTA).date()
        # Fixed clock: “hoy a las …” is a calendar request and must be future.
        fixed = calendar_due('hoy a las 23:59', WALL)
        expected = int(datetime(today.year, today.month, today.day, 23, 59, tzinfo=BOGOTA).timestamp() * 1000)
        self.assertEqual(fixed, (expected, 0))
        # Countdown: “hoy en …” retains the explicit relative duration and
        # must not be interpreted as a missing calendar hour.
        self.assertEqual(calendar_due('hoy en dos minutos', WALL), (WALL + 120000, 120000))
        self.assertEqual(calendar_due('hoy dentro de dos minutos', WALL), (WALL + 120000, 120000))
        # Invalid/zero countdowns remain rejected rather than repaired.
        with self.assertRaises(ValueError): calendar_due('hoy en cero minutos', WALL)

    def test_malformed_month_multi_action_negation_and_recurrence_never_execute(self):
        for human in ('recuérdame regar el jardín para el dos de nobiembre del 2026 a las 5pm',
                'no me recuerdes regar el jardín en tres días a las 5pm',
                'ayer dije recuérdame regar el jardín en tres días a las 5pm',
                'recuérdame regar el jardín en tres días a las 5pm y crea una alarma',
                'recuérdame regar el jardín y pon una alarma en tres días a las 5pm',
                'recuérdame regar el jardín cada tres días a las 5pm'):
            with self.subTest(human=human), self.assertRaises(ValueError): current_event('REMINDER',human,WALL,True)

    def test_day_only_asks_hour_without_inventing_one_then_claims_once(self):
        for lifecycle in (False, True):
            for day_text in ('en tres días', 'para el veinticuatro de octubre del dos mil veintiséis'):
                draft = AgendaDialogue(64, lifecycle, True)
                first = turn(1,'recuérdame regar el jardín')
                self.assertEqual(draft.begin('REMINDER',first,100000)['text'],WHEN);self.heard(draft,first)
                day_turn = turn(2,day_text); question = self.answer(draft,day_turn)
                self.assertEqual(question['text'],hour_question(date(2026,10,24)));self.assertNotIn('due_ms',question)
                self.heard(draft,day_turn)
                ambiguous = turn(3,'a las cinco'); question = self.answer(draft,ambiguous)
                self.assertEqual(question['text'],hour_question(date(2026,10,24)));self.assertIsNone(draft.prepared)
                self.heard(draft,ambiguous)
                last = turn(4,'a las cinco pm'); proposal = self.answer(draft,last)
                self.assertEqual(proposal['kind'],'guided_agenda_proposal');self.assertEqual(proposal['due_ms'],1792879200000)
                self.assertEqual(proposal['when_text'],last['text']);self.assertEqual(proposal['delay_ms'],0)
                self.assertEqual(draft.claim(proposal,last,last['calendar_boot_ms'])['text'],'regar el jardín')
                self.assertIsNone(draft.day)
                with self.assertRaises(ValueError): draft.claim(proposal,last,last['calendar_boot_ms'])

    def test_date_only_whole_request_opens_hour_question_after_native_selection(self):
        model = tools_fixture.AssistantToolsTests().model();model.anchor_wall=WALL
        model.enable_alarm_lifecycle();model.enable_basic_assistant()
        model.enable_reminder_clarification();model.enable_reminder_deletion()
        for human in ('recuérdame regar el jardín en tres días',
                'recuérdame regar el jardín para el veinticuatro de octubre del 2026'):
            result = model.interpret_result(native('create_reminder'),'tool_calls',human)
            self.assertEqual(result,dict(kind='agenda_dialogue_start',event_kind='REMINDER'))
            draft=AgendaDialogue(64,True,True);first=turn(1,human)
            self.assertEqual(draft.begin('REMINDER',first,100000)['text'],hour_question(date(2026,10,24)))
            self.assertEqual(draft.body,'regar el jardín');self.assertIsNone(draft.prepared)
            self.heard(draft,first);last=turn(2,'17:00');self.assertEqual(self.answer(draft,last)['due_ms'],1792879200000)
        # No native call, wrong selected kind, or model-supplied fields cannot supply authorization.
        for message,finish in (({'content':'Hecho'},'stop'),(native('create_alarm'),'tool_calls'),
                (native('create_reminder',when_text='en tres días'),'tool_calls')):
            self.assertEqual(model.interpret_result(message,finish,human)['kind'],'tools_rejected')

    def test_date_anchor_is_frozen_across_midnight_and_explicit_replacement_is_allowed(self):
        draft=AgendaDialogue(64,True,True)
        first=turn(1,'recuérdame regar el jardín en tres días',1792645140000)  # 21st 23:59 local.
        draft.begin('REMINDER',first,100000);self.heard(draft,first)
        last=turn(2,'5pm',1792645260000)  # 22nd 00:01; do not shift the saved 24th to the 25th.
        self.assertEqual(self.answer(draft,last)['due_ms'],1792879200000)
        draft=AgendaDialogue(64,True,True);first=turn(1,'recuérdame regar el jardín en tres días')
        draft.begin('REMINDER',first,100000);self.heard(draft,first)
        last=turn(2,'el dos de noviembre del 2026 a las 5pm')
        self.assertEqual(self.answer(draft,last)['due_ms'],1793656800000)

    def test_unheard_interrupted_expired_foreign_and_cancelled_date_fields_cannot_authorize(self):
        def ready():
            draft=AgendaDialogue(64,True,True);first=turn(1,'recuérdame regar el jardín en tres días')
            draft.begin('REMINDER',first,100000);return draft,first
        draft,first=ready()
        with self.assertRaises(ValueError):self.answer(draft,turn(2,'5pm'))
        self.assertIsNone(draft.day)
        draft,first=ready();draft.spoken(first['id'],False,100300)
        self.assertIsNone(self.answer(draft,turn(2,'5pm')));self.assertIsNone(draft.day)
        draft,first=ready();self.heard(draft,first)
        with self.assertRaises(ValueError): draft.answer(dict(turn(2,'5pm'),calendar_boot_ms=400001,speech_end=400000),400001)
        draft,first=ready();self.heard(draft,first);last=turn(2,'5pm');proposal=self.answer(draft,last)
        for key,value in (('due_ms',1793656800000),('text','otro contenido'),('dialogue_origin_turn_id','f'*32)):
            with self.assertRaises(ValueError):draft.claim(dict(proposal,**{key:value}),last,103000)
        draft,first=ready();self.heard(draft,first)
        self.assertEqual(self.answer(draft,turn(2,'cancela el recordatorio'))['kind'],'agenda_cancelled');self.assertIsNone(draft.day)

    def test_invalid_replacement_clears_partial_date_never_fuzzy_repairs_month(self):
        draft=AgendaDialogue(64,True,True);first=turn(1,'recuérdame regar el jardín en tres días')
        draft.begin('REMINDER',first,100000);self.heard(draft,first)
        bad=turn(2,'el dos de nobiembre del 2026 a las 5pm')
        self.assertEqual(self.answer(draft,bad)['text'],INVALID_TIME);self.assertIsNone(draft.day)
        self.heard(draft,bad);self.assertEqual(self.answer(draft,turn(3,'5pm'))['text'],INVALID_TIME)

    def test_trial_date_question_has_no_grant_then_receipt_and_continued_listening(self):
        now=[100000];trial=AssistantToolsTrial(lambda:now[0],guided_agenda=True,alarm_lifecycle=True,
            maximum_sequence=64,reminder_read=True,reminder_clarification=True,reminder_deletion=True)
        first=turn(1,'recuérdame regar el jardín en tres días')
        question=trial.model_result(dict(kind='agenda_dialogue_start',event_kind='REMINDER'),first,'a'*32,'b'*32,400000)
        self.assertNotIn('event',question);self.assertIsNone(trial.inflight);self.assertFalse(trial.consumed)
        trial.spoken(first['id'],True)
        last=turn(2,'5pm');now[0]=103000;grant=trial.human(last,'a'*32,'b'*32,400000)['event']
        self.assertEqual(grant['due_ms'],1792879200000);self.assertEqual(grant['delay_ms'],0)
        self.assertEqual(grant['text'],'regar el jardín');self.assertEqual(len(trial.consumed),1)
        packet={key:grant[key] for key in ('event_id','nonce','event_kind','due_ms','request_turn_id','request_sequence')}
        packet.update(action='agenda_result',session_id='a'*32,context_id='b'*32,status='CREATED',weekday=0,minute_of_day=-1)
        receipt=trial.result(packet,'a'*32,'b'*32)
        self.assertEqual(receipt['actions_executed'],1);self.assertEqual(receipt['close_reason'],'');self.assertTrue(receipt['offered_more'])
        self.assertIsNone(trial.result(packet,'a'*32,'b'*32))


if __name__ == '__main__': unittest.main()
