"""Tool effects govern dialogue lifetime; model prose never authorizes a tool.

Only create_timer is registered. Read/high-risk policies below describe behavior,
not implementations or permission to enable new tools.
"""
from dataclasses import dataclass


@dataclass(frozen=True)
class ToolPolicy:
    effect: str
    risk: str

    def route(self, *, validated, standalone):
        if self.effect not in {'read', 'write'} or self.risk not in {'low', 'high'}:
            raise ValueError('Unknown tool behavior')
        if not validated: return 'clarify'
        if self.effect == 'read': return 'respond_continue'
        if self.risk == 'high': return 'require_confirmation'
        return 'execute_close' if standalone else 'execute_continue'


TIMER_POLICY = ToolPolicy('write', 'low')
