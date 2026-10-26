"""Check catalogue parity, formatting placeholders and source lookup coverage offline."""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
TOKEN = re.compile(r'//[^\n]*|/\*.*?\*/|\'(?:\\.|[^\'\\])*\'|"(?:\\.|[^"\\])*"', re.S)
LITERAL = re.compile(r'"(?:\\.|[^"\\])*"')
FORMAT = re.compile(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]')


def arguments(source, start):
    depth, at, begin, values = 1, start, start, []
    while at < len(source):
        token = TOKEN.match(source, at)
        if token:
            at = token.end()
            continue
        char = source[at]
        if char == '(':
            depth += 1
        elif char == ')':
            depth -= 1
            if depth == 0:
                return values + [source[begin:at].strip()]
        elif char == ',' and depth == 1:
            values.append(source[begin:at].strip())
            begin = at + 1
        at += 1
    return []


def main():
    catalogues = {p.stem: json.loads(p.read_text(encoding='utf-8')) for p in (ROOT / 'app/assets/i18n').glob('*.json')}
    english = catalogues['en']
    issues = []
    if set(catalogues) != {'en','es','pt','fr','de','it'}:
        issues.append('Expected all six UI language catalogues')
    for language, values in catalogues.items():
        if set(values) != set(english):
            issues.append(language + ': catalogue keys differ')
        for key, value in values.items():
            if not isinstance(value, str) or not value.strip() and key != '':
                issues.append(language + ': empty translation ' + repr(key))
            elif sorted(FORMAT.findall(key)) != sorted(FORMAT.findall(value)):
                issues.append(language + ': formatting placeholders differ ' + repr(key))
    used = set()
    for folder in ('app/src/tv/vela/launcher', 'lite/src/tv/vela/launcher'):
        for path in (ROOT / folder).glob('*.java'):
            source = path.read_text(encoding='utf-8')
            for match in re.finditer(r'(?<!\w)(L10n\.text|Ui\.tr|Ui\.problem|VelaWorkspace\.t|LiteTvUi\.t|t)\s*\(', source):
                values = arguments(source, match.end())
                if match.group(1) == 'L10n.text':
                    value = values[0] if len(values) == 1 else ''
                elif len(values) in (2, 3):
                    value = values[-2]
                else:
                    continue
                if not LITERAL.fullmatch(value):
                    continue
                key = json.loads(value)
                used.add(key)
                if key not in english:
                    issues.append(path.relative_to(ROOT).as_posix() + ': missing lookup ' + repr(key))
    mobile = (ROOT / 'app/assets/connect.html').read_text(encoding='utf-8')
    for key in re.findall(r'data-(?:i18n|placeholder)="([^"]+)"', mobile):
        if key not in english:
            issues.append('Mobile page: missing lookup ' + repr(key))
    if issues:
        raise SystemExit('\n'.join(issues))
    print(f'PASS {len(english)} catalogue keys in {", ".join(sorted(catalogues))}; placeholder parity, {len(used)} literal source lookups and mobile page labels')


if __name__ == '__main__':
    main()
