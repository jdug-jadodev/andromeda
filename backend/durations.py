"""Exact duration conversion for a prospective tool contract. No phrase matching."""
from decimal import Decimal, InvalidOperation
import math


def duration_seconds(quantity, unit):
    if type(quantity) not in (int, float) or (type(quantity) is float and not math.isfinite(quantity)):
        raise ValueError('Expected a finite numeric quantity')
    if not 0 < quantity <= 86400:
        raise ValueError('Quantity out of range')
    factors = {'seconds': 1, 'minutes': 60, 'hours': 3600}
    if not isinstance(unit, str) or unit not in factors:
        raise ValueError('Unknown duration unit')
    try:
        seconds = Decimal(str(quantity)) * factors[unit]
    except InvalidOperation as error:
        raise ValueError('Invalid quantity') from error
    if not 1 <= seconds <= 86400 or seconds != seconds.to_integral_value():
        raise ValueError('Expected whole seconds between 1 and 86400')
    return int(seconds)
