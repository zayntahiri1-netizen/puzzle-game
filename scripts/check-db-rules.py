#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
يتحقّق من database.rules.json كما يتحقّق منه Firebase CLI قبل النشر.

سببه: فحص JSON العادي (json.load) يقبل مفاتيح مثل "//" على أنّها أسماء
عقد، بينما Firebase يرفض أي مفتاح في المستوى الأعلى غير "rules" ويقول:

    Error: Syntax error in database rules:
    2:3: Expected 'rules' property.

فيفشل النشر بعد رفع الموقع. هذا الفحص يكشفه قبل ذلك.

الاستعمال:  python3 scripts/check-db-rules.py [المسار]
الخروج:     0 سليم، 1 خطأ
"""
import json, sys, os

path = sys.argv[1] if len(sys.argv) > 1 else 'database.rules.json'
if not os.path.isfile(path):
    print(f'✗ لم أجد {path}')
    sys.exit(1)

src = open(path, encoding='utf-8').read()


def strip_comments(s):
    """يحذف /* */ و // مع احترام ما بداخل النصوص."""
    out, i, n = [], 0, len(s)
    while i < n:
        c  = s[i]
        nx = s[i + 1] if i + 1 < n else ''
        if c == '"':
            out.append(c); i += 1
            while i < n:
                if s[i] == '\\':
                    out.append(s[i:i + 2]); i += 2; continue
                out.append(s[i])
                if s[i] == '"':
                    i += 1; break
                i += 1
            continue
        if c == '/' and nx == '*':
            j = s.find('*/', i + 2)
            i = (j + 2) if j != -1 else n
            continue
        if c == '/' and nx == '/':
            j = s.find('\n', i)
            i = j if j != -1 else n
            continue
        out.append(c); i += 1
    return ''.join(out)


try:
    data = json.loads(strip_comments(src))
except Exception as e:
    print(f'✗ {path}: JSON غير سليم بعد حذف التعليقات — {e}')
    sys.exit(1)

if not isinstance(data, dict):
    print(f'✗ {path}: الجذر يجب أن يكون كائناً')
    sys.exit(1)

keys = list(data.keys())
if keys != ['rules']:
    extra = [k for k in keys if k != 'rules']
    print(f'✗ {path}: Firebase يقبل «rules» وحده في المستوى الأعلى.')
    if extra:
        print(f'   مفاتيح زائدة: {extra}')
        print('   اكتب التعليقات بصيغة /* ... */ لا كمفاتيح JSON.')
    else:
        print('   المفتاح «rules» مفقود.')
    sys.exit(1)

rules = data['rules']
bad = [k for k in rules if k.startswith('//')]
if bad:
    print(f'✗ {path}: مفاتيح تعليق داخل rules تُفهم كأسماء عقد: {bad}')
    sys.exit(1)

pay = rules.get('payments', {})
closed = pay.get('.read') is False and pay.get('.write') is False
paths = [k for k in rules if not k.startswith('.')]

print(f'✅ {path} سليم')
print(f'   المسارات: {", ".join(paths)}')
print(f'   الجذر مغلق: {rules.get(".read") is False and rules.get(".write") is False}')
print(f'   payments مغلق: {closed}')
sys.exit(0)
