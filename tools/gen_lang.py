"""Writes the six language files. Run from the repository root: python3 -I tools/gen_lang.py
Terms follow the vanilla 1.21.1 translations (chest, barrel, spawner, materials)."""
import json
import pathlib

LANG = pathlib.Path(__file__).resolve().parents[1] / 'src/main/resources/assets/chest_transporter/lang'
TIERS = ['wooden', 'copper', 'iron', 'golden', 'diamond', 'netherite']

NAMES = {
    'en_us': ['Wooden Chest Transporter', 'Copper Chest Transporter', 'Iron Chest Transporter', 'Golden Chest Transporter',
              'Diamond Chest Transporter', 'Netherite Chest Transporter'],
    'ru_ru': ['Деревянная тележка для сундуков', 'Медная тележка для сундуков', 'Железная тележка для сундуков',
              'Золотая тележка для сундуков', 'Алмазная тележка для сундуков', 'Незеритовая тележка для сундуков'],
    'uk_ua': ['Дерев’яний візок для скринь', 'Мідний візок для скринь', 'Залізний візок для скринь',
              'Золотий візок для скринь', 'Діамантовий візок для скринь', 'Незеритовий візок для скринь'],
    'de_de': ['Hölzerne Truhenkarre', 'Kupferne Truhenkarre', 'Eiserne Truhenkarre', 'Goldene Truhenkarre',
              'Diamantene Truhenkarre', 'Netherit-Truhenkarre'],
    'es_es': ['Carretilla de cofres de madera', 'Carretilla de cofres de cobre', 'Carretilla de cofres de hierro',
              'Carretilla de cofres de oro', 'Carretilla de cofres de diamante', 'Carretilla de cofres de netherita'],
    'pt_br': ['Carrinho de Baús de Madeira', 'Carrinho de Baús de Cobre', 'Carrinho de Baús de Ferro',
              'Carrinho de Baús de Ouro', 'Carrinho de Baús de Diamante', 'Carrinho de Baús de Netherite'],
}

TEXTS = {
    'en_us': {
        'item.chest_transporter.empty': 'Right-click a chest, barrel or spawner to load it',
        'item.chest_transporter.carrying': 'Carrying: %s',
        'item.chest_transporter.stacks': '%s stacks inside',
        'item.chest_transporter.loot': 'Unopened loot',
        'item.chest_transporter.spawner': 'Spawns: %s',
        'item.chest_transporter.moves_left': 'Moves left: %s',
        'message.chest_transporter.not_transportable': 'This block cannot be carried',
        'message.chest_transporter.spawner_disabled': 'Spawners cannot be carried on this server',
        'message.chest_transporter.spawner_tier': 'This truck is not strong enough for a spawner',
        'message.chest_transporter.protected': 'You are not allowed to do that here',
        'message.chest_transporter.locked': 'This container is locked',
        'message.chest_transporter.too_heavy': 'Too much inside to carry',
        'message.chest_transporter.no_room': 'No room to put it down here',
    },
    'ru_ru': {
        'item.chest_transporter.empty': 'ПКМ по сундуку, бочке или рассаднику — погрузить',
        'item.chest_transporter.carrying': 'Везёт: %s',
        'item.chest_transporter.stacks': 'Внутри стаков: %s',
        'item.chest_transporter.loot': 'Неоткрытая добыча',
        'item.chest_transporter.spawner': 'Призывает: %s',
        'item.chest_transporter.moves_left': 'Осталось перевозок: %s',
        'message.chest_transporter.not_transportable': 'Этот блок нельзя увезти',
        'message.chest_transporter.spawner_disabled': 'На этом сервере рассадники перевозить нельзя',
        'message.chest_transporter.spawner_tier': 'Эта тележка слишком слабая для рассадника',
        'message.chest_transporter.protected': 'Здесь вам это делать нельзя',
        'message.chest_transporter.locked': 'Этот контейнер заперт',
        'message.chest_transporter.too_heavy': 'Внутри слишком много, не увезти',
        'message.chest_transporter.no_room': 'Здесь некуда поставить',
    },
    'uk_ua': {
        'item.chest_transporter.empty': 'ПКМ по скрині, діжці чи породжувачу — завантажити',
        'item.chest_transporter.carrying': 'Везе: %s',
        'item.chest_transporter.stacks': 'Стосів усередині: %s',
        'item.chest_transporter.loot': 'Невідкрита здобич',
        'item.chest_transporter.spawner': 'Породжує: %s',
        'item.chest_transporter.moves_left': 'Залишилося перевезень: %s',
        'message.chest_transporter.not_transportable': 'Цей блок не можна перевезти',
        'message.chest_transporter.spawner_disabled': 'На цьому сервері породжувачі перевозити не можна',
        'message.chest_transporter.spawner_tier': 'Цей візок заслабкий для породжувача',
        'message.chest_transporter.protected': 'Тут вам цього робити не можна',
        'message.chest_transporter.locked': 'Цей контейнер замкнено',
        'message.chest_transporter.too_heavy': 'Усередині забагато, не перевезти',
        'message.chest_transporter.no_room': 'Тут немає місця, щоб поставити',
    },
    'de_de': {
        'item.chest_transporter.empty': 'Rechtsklick auf eine Truhe, ein Fass oder einen Spawner zum Aufladen',
        'item.chest_transporter.carrying': 'Geladen: %s',
        'item.chest_transporter.stacks': '%s Stapel darin',
        'item.chest_transporter.loot': 'Ungeöffnete Beute',
        'item.chest_transporter.spawner': 'Erzeugt: %s',
        'item.chest_transporter.moves_left': 'Verbleibende Fahrten: %s',
        'message.chest_transporter.not_transportable': 'Dieser Block kann nicht transportiert werden',
        'message.chest_transporter.spawner_disabled': 'Spawner können auf diesem Server nicht transportiert werden',
        'message.chest_transporter.spawner_tier': 'Diese Karre ist für einen Spawner nicht stark genug',
        'message.chest_transporter.protected': 'Das darfst du hier nicht',
        'message.chest_transporter.locked': 'Dieser Behälter ist verschlossen',
        'message.chest_transporter.too_heavy': 'Zu viel Inhalt zum Transportieren',
        'message.chest_transporter.no_room': 'Hier ist kein Platz zum Abstellen',
    },
    'es_es': {
        'item.chest_transporter.empty': 'Clic derecho en un cofre, barril o generador para cargarlo',
        'item.chest_transporter.carrying': 'Lleva: %s',
        'item.chest_transporter.stacks': '%s montones dentro',
        'item.chest_transporter.loot': 'Botín sin abrir',
        'item.chest_transporter.spawner': 'Genera: %s',
        'item.chest_transporter.moves_left': 'Viajes restantes: %s',
        'message.chest_transporter.not_transportable': 'Este bloque no se puede llevar',
        'message.chest_transporter.spawner_disabled': 'En este servidor no se pueden llevar generadores',
        'message.chest_transporter.spawner_tier': 'Esta carretilla no es lo bastante fuerte para un generador',
        'message.chest_transporter.protected': 'No puedes hacer eso aquí',
        'message.chest_transporter.locked': 'Este contenedor está cerrado con llave',
        'message.chest_transporter.too_heavy': 'Hay demasiado dentro para llevarlo',
        'message.chest_transporter.no_room': 'No hay sitio para dejarlo aquí',
    },
    'pt_br': {
        'item.chest_transporter.empty': 'Clique com o botão direito num baú, barril ou gerador para carregá-lo',
        'item.chest_transporter.carrying': 'Carregando: %s',
        'item.chest_transporter.stacks': '%s pilhas dentro',
        'item.chest_transporter.loot': 'Saque não aberto',
        'item.chest_transporter.spawner': 'Gera: %s',
        'item.chest_transporter.moves_left': 'Viagens restantes: %s',
        'message.chest_transporter.not_transportable': 'Este bloco não pode ser carregado',
        'message.chest_transporter.spawner_disabled': 'Geradores não podem ser carregados neste servidor',
        'message.chest_transporter.spawner_tier': 'Este carrinho não é forte o bastante para um gerador',
        'message.chest_transporter.protected': 'Você não pode fazer isso aqui',
        'message.chest_transporter.locked': 'Este recipiente está trancado',
        'message.chest_transporter.too_heavy': 'Há coisas demais dentro para carregar',
        'message.chest_transporter.no_room': 'Não há espaço para colocá-lo aqui',
    },
}

if __name__ == '__main__':
    LANG.mkdir(parents=True, exist_ok=True)
    for lang, names in NAMES.items():
        data = {f'item.chest_transporter.{tier}_chest_transporter': name for tier, name in zip(TIERS, names)}
        data.update(TEXTS[lang])
        (LANG / f'{lang}.json').write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print(lang, len(data))
