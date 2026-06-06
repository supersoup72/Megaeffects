import sys
src = open(sys.argv[1], 'rb').read()
out = 'static const char tccdefs_str[]={\n'
for i, b in enumerate(src):
    out += str(b) + ','
    if (i+1) % 16 == 0:
        out += '\n'
out += '0};\n'
open(sys.argv[2], 'w').write(out)
print('tccdefs_.h written:', len(out), 'bytes')
