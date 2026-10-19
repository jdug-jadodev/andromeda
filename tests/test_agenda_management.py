"""Offline source/schema gates, not Android SQLite, scheduler or UI acceptance."""
import json
from pathlib import Path
import re
import sqlite3
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'android-voice/src/local/doja/voice'


class AgendaManagementTests(unittest.TestCase):
    def schema(self):
        source = (SOURCE / 'AgendaStore.java').read_text(encoding='utf-8')
        match = re.search(r'db\.execSQL\(("CREATE TABLE agenda .*?)\);', source, re.S)
        self.assertIsNotNone(match)
        ddl = ''.join(json.loads(part) for part in re.findall(r'"(?:[^"\\]|\\.)*"', match[1]))
        database = sqlite3.connect(':memory:')
        self.addCleanup(database.close)
        database.execute(ddl)
        return database

    def test_agenda_schema_keeps_only_event_fields_not_conversation(self):
        database = self.schema()
        columns = [row[1] for row in database.execute('PRAGMA table_info(agenda)')]
        self.assertEqual(columns, ['id', 'kind', 'text', 'due_ms', 'zone', 'state', 'revision', 'elapsed_due'])
        self.assertNotIn('request', columns)
        self.assertNotIn('audio', columns)
        self.assertNotIn('hash', columns)

    def test_sql_rejects_invalid_kind_zone_time_state_and_content(self):
        database = self.schema()
        valid = ['00000000-0000-4000-8000-000000000001', 'REMINDER', 'Frase pública de prueba', 1792328400000,
                 'America/Bogota', 'PENDING', 1, 0]
        database.execute('INSERT INTO agenda VALUES (?,?,?,?,?,?,?,?)', valid)
        database.execute('DELETE FROM agenda')
        for index, value in [(1, 'TIMER'), (3, 0), (4, 'UTC'), (5, 'DELIVERED'), (6, 0), (2, 'a' * 161), (2, ''), (2, ' '), (5, 'CANCEL_PENDING'), (1, 'ALARM')]:
            entry = valid.copy(); entry[index] = value
            with self.subTest(index=index, value=value), self.assertRaises(sqlite3.IntegrityError):
                database.execute('INSERT INTO agenda VALUES (?,?,?,?,?,?,?,?)', entry)

    def test_selected_id_redaction_and_deletion_do_not_touch_other_kind(self):
        database = self.schema()
        reminder = ['00000000-0000-4000-8000-000000000001', 'REMINDER', 'Prueba pública', 1792328400000, 'America/Bogota', 'FAILED', 1, 0]
        alarm = ['00000000-0000-4000-8000-000000000002', 'ALARM', '', 1792328400000, 'America/Bogota', 'SCHEDULED', 1, 0]
        database.executemany('INSERT INTO agenda VALUES (?,?,?,?,?,?,?,?)', [reminder, alarm])
        self.assertEqual(database.execute("UPDATE agenda SET text='',state='CANCEL_PENDING',revision=revision+1 WHERE id=? AND kind=? AND revision=?", [reminder[0], 'REMINDER', 2]).rowcount, 0)
        self.assertEqual(database.execute("UPDATE agenda SET text='',state='CANCEL_PENDING',revision=revision+1 WHERE id=? AND kind=? AND revision=?", [reminder[0], 'REMINDER', 1]).rowcount, 1)
        self.assertEqual(database.execute('SELECT text,state FROM agenda WHERE id=?', [reminder[0]]).fetchone(), ('', 'CANCEL_PENDING'))
        self.assertEqual(database.execute("DELETE FROM agenda WHERE id=? AND kind=? AND revision=? AND state='CANCEL_PENDING' AND text=''", [reminder[0], 'REMINDER', 1]).rowcount, 0)
        self.assertEqual(database.execute("DELETE FROM agenda WHERE id=? AND kind=? AND revision=? AND state='CANCEL_PENDING' AND text=''", [reminder[0], 'REMINDER', 2]).rowcount, 1)
        self.assertEqual(database.execute('SELECT * FROM agenda').fetchall(), [tuple(alarm)])

    def test_agenda_store_has_no_llm_network_or_capture_and_keeps_transaction_guards(self):
        source = '\n'.join((SOURCE / name).read_text(encoding='utf-8') for name in
                           ['AgendaStore.java', 'AgendaCancellation.java', 'AgendaEntry.java', 'AgendaOutputs.java'])
        for forbidden in ['TimerStore', 'VoiceConnection', 'SilenceListener', 'TextToSpeech', 'AudioRecord', 'startService(',
                          'startForegroundService(', 'setRepeating', 'java.net.', 'FileOutputStream', 'DROP TABLE']:
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, source)
        self.assertIn('PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE', source)
        self.assertIn('PRAGMA secure_delete=ON', source)
        self.assertIn('db.rawQuery("PRAGMA secure_delete=ON", null)', source)
        self.assertNotIn('db.execSQL("PRAGMA secure_delete=ON")', source)
        self.assertIn('configured.getInt(0) != 1', source)
        self.assertIn('helper.setWriteAheadLoggingEnabled(false)', source)
        self.assertIn('LIMIT 50', source)
        self.assertIn('throw new SQLiteException("Agenda migration required")', source)
        self.assertIn('Status create(AgendaPermit permit)', source)
        self.assertIn('AlarmManager.RTC_WAKEUP : AlarmManager.ELAPSED_REALTIME_WAKEUP', source)
        self.assertIn('if (!delivered) return changeState(current, AgendaEntry.State.FAILED)', source)
        self.assertIn('if (!created(id))', source)

    def test_manual_ui_uses_same_store_selected_confirmation_and_preserves_timeout(self):
        source = (SOURCE / 'MainActivity.java').read_text(encoding='utf-8')
        self.assertIn('button(second, "Alarmas", () -> agendaOptions(AgendaEntry.Kind.ALARM))', source)
        self.assertIn('button(second, "Recordatorios", () -> agendaOptions(AgendaEntry.Kind.REMINDER))', source)
        self.assertIn('"Eliminar solo " + name', source)
        self.assertIn('AgendaCancellation.remove(entry, store, AgendaStore.effects(this))', source)
        self.assertIn('optionsDialog != loading', source)
        self.assertIn('agendaWorker.shutdown()', source)
        self.assertNotIn('agendaWorker.shutdownNow()', source)
        self.assertIn('optionsScroll.addView(panel', source)
        policy = (SOURCE / 'UiPolicy.java').read_text(encoding='utf-8')
        self.assertIn('50000', policy)

    def test_delivery_components_are_private_and_boot_only_restores_not_capture(self):
        namespace = '{http://schemas.android.com/apk/res/android}'
        manifest = ET.parse(ROOT / 'android-voice/AndroidManifest.xml').getroot()
        application = manifest.find('application')
        self.assertEqual(application.attrib[namespace + 'allowBackup'], 'false')
        components = [entry for entry in application if 'Agenda' in entry.attrib.get(namespace + 'name', '')]
        self.assertEqual(len(components), 2)
        self.assertTrue(all(entry.attrib[namespace + 'exported'] == 'false' for entry in components))
        boot = (SOURCE / 'BootReceiver.java').read_text(encoding='utf-8')
        self.assertIn('AgendaStore.restore(context)', boot)
        self.assertNotIn('AgendaDeliveryService', boot)
        delivery = (SOURCE / 'AgendaDeliveryService.java').read_text(encoding='utf-8')
        self.assertIn('public void onDone(String utterance)', delivery)
        self.assertIn('end(true)', delivery)
        self.assertIn('store.finish(finished, delivered)', delivery)
        self.assertNotIn('VoiceConnection', delivery)

    def test_android_agenda_fixture_tests_never_use_real_output_or_scheduler(self):
        for name in ['AgendaInstrumentation.java', 'AgendaUiInstrumentation.java', 'AgendaTransportInstrumentation.java', 'AlarmAsrInstrumentation.java']:
            source = (SOURCE / name).read_text(encoding='utf-8')
            with self.subTest(name=name):
                for forbidden in ['new AudioRecord', 'new TextToSpeech', 'startService(', 'startForegroundService(', 'setExactAndAllowWhileIdle(']:
                    self.assertNotIn(forbidden, source)
                self.assertIn('"microphone_opened",', source)
                self.assertIn('"audio_played",', source)
                self.assertIn('"real_scheduler",', source)
        source = (SOURCE / 'AgendaTransportInstrumentation.java').read_text(encoding='utf-8')
        self.assertIn('"agenda-isolated-test.db",fake', source)
        self.assertIn('"synthetic_pcm"', source)
        self.assertIn('"fixture_validated"', source.replace('\\"', '"'))
        alarm = (SOURCE / 'AlarmAsrInstrumentation.java').read_text(encoding='utf-8')
        self.assertIn('"synthetic_pcm"', alarm)
        self.assertIn('"agenda-alarm-asr-test.db"', alarm)
        self.assertIn('permit.value.kind == AgendaEntry.Kind.ALARM', alarm)
        self.assertIn('permit.value.delay == 120000', alarm)
        self.assertIn('"real_alarm_sounded", false', alarm)
        self.assertNotIn('new AudioRecord', alarm)
        self.assertNotIn('RingtoneManager', alarm)

    def test_transport_fixture_is_explicit_and_bounded_not_operational_inference(self):
        source = (ROOT / 'scripts/test-agenda-transport.py').read_text(encoding='utf-8')
        self.assertIn('signal.alarm(100)', source)
        self.assertIn('wire.session = fixture_session', source)
        self.assertIn('native.AssistantToolsModel = FixtureModel', source)
        self.assertIn("'synthetic_pcm',resource_ok,assistant_tools=True", source)
        self.assertIn('session_actor_simulated=True', source)
        self.assertNotIn('subprocess', source)
        self.assertNotIn('synthesize', source)


if __name__ == '__main__':
    unittest.main()
