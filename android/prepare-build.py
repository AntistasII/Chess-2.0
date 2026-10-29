#!/usr/bin/env python3
"""Build Android-compatible sources from the shared engine; never read the local secret YAML."""
from pathlib import Path
import json
import re
import shutil

root = Path(__file__).resolve().parent.parent
out = root / 'android/app/build/generated/offline'
sources = out / 'java'
assets = out / 'assets'
if out.exists():
    shutil.rmtree(out)
sources.mkdir(parents=True)
assets.mkdir()
backend = root / 'backend/src/main/java'
packages = ('ai', 'board', 'cards', 'pieces', 'game', 'opponent', 'serialization')
files = [p for package in packages for p in (backend / 'chess' / package).rglob('*.java')]
files += [backend / 'chess/config' / name for name in ('GameProperties.java', 'OpenAiProperties.java')]
card_classes = sorted(p.stem for p in (backend / 'chess/cards/impl').glob('*.java') if '@CardDefinition(' in p.read_text())
for path in files:
    text = path.read_text()
    text = re.sub(r'^import org\.springframework[^\n]+\n', '', text, flags=re.M)
    text = re.sub(r'^@ConfigurationProperties[^\n]+\n', '', text, flags=re.M)
    if path.name == 'CardRegistry.java':
        start = text.index('    public static CardRegistry scan(')
        end = text.index('    public Card create(', start)
        registrations = ', '.join('chess.cards.impl.' + name + '.class' for name in card_classes)
        text = text[:start] + '    public static CardRegistry scan(String ignored) {\n        return new CardRegistry(List.of(' + registrations + '));\n    }\n\n' + text[end:]
    if path.name == 'ChatGptOpponent.java':
        text = text.replace('import java.net.http.*;', '')
        start = text.index('    public static ChatGptOpponent fromConfig(')
        end = text.index('    /** Новый ключ', start)
        text = text[:start] + text[end:]
        text = text.replace('    interface Transport', '    public interface Transport').replace('    record Reply', '    public record Reply')
        text = text.replace('    ChatGptOpponent(', '    public ChatGptOpponent(')
        text = text.replace('Для ChatGPT заполните openai.api-key в application.yml и перезапустите сервер.', 'Для игры с ChatGPT введите ключ в окне новой партии.')
        text = text.replace('Укажите действующий ключ в окне новой партии или в openai.api-key в application.yml.', 'Укажите действующий ключ в окне новой партии.')
    text = text.replace('Files.readString(path, java.nio.charset.StandardCharsets.UTF_8)', 'new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8)')
    text = text.replace('Files.readString(file)', 'new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8)')
    text = text.replace('Files.writeString(temporary, JsonCodec.toJson(Map.of("lessons", lessons)))', 'Files.write(temporary, JsonCodec.toJson(Map.of("lessons", lessons)).getBytes(java.nio.charset.StandardCharsets.UTF_8))')
    text = text.replace('Files.writeString(temp, JsonCodec.toJson(encode(g)))', 'Files.write(temp, JsonCodec.toJson(encode(g)).getBytes(java.nio.charset.StandardCharsets.UTF_8))')
    text = text.replace('.getFirst()' , '.get(0)').replace('.toList()', '.collect(java.util.stream.Collectors.toList())')
    dest = sources / path.relative_to(backend)
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(text)

# Built-in defaults use only nested scalar mappings. Fail on unsupported YAML instead of silently ignoring it.
config = {}
stack = [(-1, config)]
for line in (root / 'backend/src/main/resources/application.yml').read_text().splitlines():
    if not line.strip() or line.lstrip().startswith('#'):
        continue
    match = re.fullmatch(r'( *)([\w-]+):(?: (.*))?', line)
    if not match:
        raise ValueError('Unsupported built-in configuration line: ' + line)
    indent, key, value = match.groups()
    level = len(indent)
    while stack[-1][0] >= level:
        stack.pop()
    parent = stack[-1][1]
    if value is None:
        parent[key] = {}
        stack.append((level, parent[key]))
    else:
        value = re.sub(r'\$\{[^:}]+:([^}]*)\}', r'\1', value)
        parent[key] = int(value) if value.isdigit() else value
# No configured key may enter the APK, including an accidentally edited built-in default.
config['openai']['api-key'] = ''
(assets / 'defaults.json').write_text(json.dumps(config, ensure_ascii=False))
shutil.copy(root / 'prompts/chatgpt-opponent.txt', assets / 'chatgpt-opponent.txt')
shutil.copytree(root / 'frontend/dist', assets / 'web')
index = assets / 'web/index.html'
index.write_text(index.read_text().replace('<head>', '<head>\n<script src="/android.js"></script>', 1))
shutil.copy(root / 'android/app/src/main/assets/android.js', assets / 'web/android.js')
print('Prepared shared engine,', len(card_classes), 'annotated card types and bundled interface.')
