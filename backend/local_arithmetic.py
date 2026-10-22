"""Bounded read-only arithmetic from the ENTIRE human question, never Python eval.

No model, tools, calls, attributes, names, IO, network or persistence. Unrecognized
language returns None so ordinary conversation keeps using the model.
"""
import ast
from decimal import Decimal, DecimalException, localcontext
import re
from doja_intents import normalized, number, ONES, TENS

HUNDREDS = {'cien':100,'ciento':100,'doscientos':200,'trescientos':300,'cuatrocientos':400,
    'quinientos':500,'seiscientos':600,'setecientos':700,'ochocientos':800,'novecientos':900}
NUMBER_WORDS = set(ONES) | set(TENS) | set(HUNDREDS) | {'y','mil'}
PREFIX = re.compile(r'^(?:por favor|me puedes|puedes|podrias|me podrias|decirme|dime|calcula|calculame|calcular|'
    r'cuanto es|cuanto son|cuanto da|cuanto seria|cual es el resultado de|el resultado de|multiplica|multiplicar|divide|dividir)\b[, ]*')
LIMIT = Decimal('1000000000000')


def spanish_number(words):
    if not words: raise ValueError('missing_number')
    if words.count('mil'):
        if words.count('mil') != 1: raise ValueError('ambiguous_number')
        at=words.index('mil'); left=spanish_number(words[:at]) if at else 1
        right=spanish_number(words[at+1:]) if at+1<len(words) else 0
        if not 1<=left<=999 or not 0<=right<=999: raise ValueError('number_outside_range')
        return left*1000+right
    if words[0] in HUNDREDS:
        tail=number(' '.join(words[1:])) if len(words)>1 else 0
        if not 0<=tail<=99: raise ValueError('ambiguous_number')
        return HUNDREDS[words[0]]+tail
    return number(' '.join(words))


def expression(human):
    if (not isinstance(human,str) or not 0<len(human)<=250
            or re.search(r'[\x00-\x1f\x7f"\'«»<>;{}\[\]`]',human)
            or any(char.isdecimal() and char not in '0123456789' for char in human)):
        raise ValueError('not_bounded_arithmetic')
    value=normalized(human).strip(' .,!¡?¿')
    value=re.sub(r'^andromeda[, ]+','',value); requested_operator=None
    for _ in range(6):
        match=PREFIX.match(value)
        if not match: break
        prefix=match.group().strip(' ,')
        operation={'multiplica':'*','multiplicar':'*','divide':'/','dividir':'/'}.get(prefix)
        if operation:
            if requested_operator: raise ValueError('ambiguous_operation')
            requested_operator=operation
        value=value[match.end():].lstrip(' ,')
    value=re.sub(r'[, ]+por favor$','',value).strip()
    value=value.replace('multiplicado por','por').replace('dividido entre','entre').replace('elevado a','^')
    if requested_operator=='/': value=re.sub(r'\bpor\b','entre',value)
    value=value.replace('abre parentesis','(').replace('cierra parentesis',')')
    tokens=re.findall(r'[0-9]+(?:[.,][0-9]+)?|[a-z]+|\*\*|[^\s]',value)
    result=[]; at=0; operators=0
    while at<len(tokens):
        token=tokens[at]
        if re.fullmatch(r'[0-9]+(?:[.,][0-9]+)?',token):
            if len(token)>30: raise ValueError('number_too_long')
            # Preserve the numeric value, including decimal comma, not a model rewrite.
            result.append(format(Decimal(token.replace(',','.')),'f')); at+=1
        elif token in NUMBER_WORDS:
            end=at+1
            while end<len(tokens) and tokens[end] in NUMBER_WORDS: end+=1
            result.append(str(spanish_number(tokens[at:end]))); at=end
        else:
            mapped={'mas':'+','menos':'-','por':'*','entre':'/','×':'*','÷':'/','^':'**'}.get(token,token)
            if mapped not in {'+','-','*','/','**','(',')'}: raise ValueError('not_whole_arithmetic_question')
            result.append(mapped); at+=1
            if mapped not in {'(',')'}: operators+=1
    if not result or not 1<=operators<=24: raise ValueError('not_arithmetic_question')
    if requested_operator and (operators!=1 or requested_operator not in result): raise ValueError('ambiguous_operation')
    value=' '.join(result)
    if len(value)>250: raise ValueError('arithmetic_too_long')
    return value


def evaluate(source):
    tree=ast.parse(source,mode='eval')
    if len(list(ast.walk(tree)))>64: raise ValueError('too_many_nodes')
    def visit(node,depth=0):
        if depth>12: raise ValueError('too_deep')
        if isinstance(node,ast.Expression): return visit(node.body,depth+1)
        if isinstance(node,ast.Constant) and type(node.value) in (int,float):
            literal=ast.get_source_segment(source,node)
            if literal is None or not re.fullmatch(r'[0-9]+(?:\.[0-9]+)?',literal): raise ValueError('invalid_number')
            value=Decimal(literal)
        elif isinstance(node,ast.UnaryOp) and isinstance(node.op,(ast.UAdd,ast.USub)):
            value=visit(node.operand,depth+1)
            if isinstance(node.op,ast.USub): value=-value
        elif isinstance(node,ast.BinOp) and isinstance(node.op,(ast.Add,ast.Sub,ast.Mult,ast.Div,ast.Pow)):
            left=visit(node.left,depth+1); right=visit(node.right,depth+1)
            if isinstance(node.op,ast.Add): value=left+right
            elif isinstance(node.op,ast.Sub): value=left-right
            elif isinstance(node.op,ast.Mult): value=left*right
            elif isinstance(node.op,ast.Div):
                if right==0: raise ZeroDivisionError()
                value=left/right
            else:
                if right!=right.to_integral_value() or not 0<=right<=12: raise ValueError('exponent_outside_range')
                value=left**int(right)
        else: raise ValueError('unsupported_arithmetic_node')
        if not value.is_finite() or abs(value)>LIMIT: raise ValueError('arithmetic_outside_range')
        return value
    with localcontext() as context:
        context.prec=28; context.Emax=128; context.Emin=-128
        return visit(tree)


def answer(human):
    try: source=expression(human)
    except (ValueError,DecimalException): return None
    try:
        value=evaluate(source)
        with localcontext() as context:
            context.prec=28; rounded=value.quantize(Decimal('0.000000000001'))
        text=format(rounded,'f').rstrip('0').rstrip('.') if rounded!=rounded.to_integral_value() else format(rounded.quantize(Decimal(1)),'f')
        if text in {'-0',''}: text='0'
        prefix='El resultado aproximado es ' if rounded!=value else 'El resultado es '
        return prefix+text.replace('.',',')+'.'
    except ZeroDivisionError: return 'No se puede dividir entre cero.'
    except (ValueError,DecimalException,OverflowError,RecursionError):
        return 'Ese cálculo supera los límites del cálculo local seguro.'
    except SyntaxError: return None
