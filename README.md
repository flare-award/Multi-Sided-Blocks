# Multi-Sided Blocks (Fabric, Minecraft 1.20.1)

Один блок — шесть независимых граней. Каждой стороне блока можно назначить текстуру
любого другого блока (ванильного или из модов). Данные хранятся отдельно для каждой
грани каждого блока и сохраняются в мире (BlockEntity/NBT).

One block, six independent faces. Each face of a block can show the texture of any other
block (vanilla or modded). Every face of every block stores its own data, saved to the
world via BlockEntity/NBT.

## Как это работает / How it works

- Поставьте **Multi-Sided Block** (крафт: кольцо из камня + бумага в центре).
- Возьмите любой блок в руку и нажмите **ПКМ** по нужной грани — грань получит его
  текстуру (с учётом биомной окраски: трава, листва, вода).
- Повторите для остальных граней. Остальные грани не меняются.
- **Shift + ПКМ** пустой рукой — сбросить грань к базовой текстуре.
- Сломанный блок выпадает предметом с сохранёнными гранями (BlockEntityTag);
  при установке грани восстанавливаются. Средняя кнопка мыши (пипетка) тоже копирует грани.

- Place a **Multi-Sided Block** (recipe: ring of stone + paper in the middle).
- Hold any block and **right-click** the face you want — that face gets its texture
  (biome tints are preserved: grass, leaves, water).
- Repeat for the other faces. Other faces are untouched.
- **Sneak + right-click** with an empty hand resets a face to the default texture.
- Breaking the block drops an item carrying the face data (BlockEntityTag); placing it
  restores the faces. Creative middle-click also copies the faces.

Пример / Example:

```
        Верх / Top
          ↑
Лево ← [Блок] → Право        север → песчаник (фасад)
          ↓                   юг → обои (интерьер)
        Низ / Bottom
```

## Технические детали / Technical details

- Каждая грань хранит полный `BlockState` источника (не только id текстуры), поэтому
  свойства блока (например, тип плиты) тоже сохраняются.
- NBT: `faces.<direction>` внутри BlockEntity (`north`, `south`, `east`, `west`, `up`, `down`).
- Рендер: кастомная `BakedModel` через Fabric Renderer API (`FabricBakedModel`).
  Текстура грани извлекается из доминантного квада модели исходного блока на этой
  грани; для не-кубических блоков используется particle-спрайт. Тонировка (tint)
  разрешается по цветовому провайдеру исходного блока.
- Совместимость с WorldEdit / Axiom: данные живут в NBT BlockEntity, поэтому
  копирование/вставка и схемы сохраняют грани.
- Ресурс-паки работают как обычно: грани показывают актуальные спрайты из атласа
  блоков, обновляясь при перезагрузке ресурсов.

- Each face stores the full `BlockState` of the source (not just a texture id), so block
  properties (e.g. slab type) are preserved too.
- NBT: `faces.<direction>` in the BlockEntity (`north`, `south`, `east`, `west`, `up`, `down`).
- Rendering: custom `BakedModel` via the Fabric Renderer API (`FabricBakedModel`). A face
  texture is taken from the dominant quad of the source block's model on that face;
  non-cube blocks fall back to their particle sprite. Tints are resolved through the
  source block's color provider.
- WorldEdit / Axiom friendly: data lives in BlockEntity NBT, so copies, pastes and
  schematics keep the faces.
- Resource packs work as usual: faces show the current atlas sprites and refresh on
  resource reload.

## Совместимость рендера / Renderer compatibility

- По умолчанию (Fabric API + Indigo) — полная поддержка.
- **Sodium**: поставьте [Indium](https://modrinth.com/mod/indium) — без него Sodium
  0.5 не вызывает Fabric Renderer API, и блоки будут показывать базовую текстуру.

- Default (Fabric API + Indigo): full support.
- **Sodium**: install [Indium](https://modrinth.com/mod/indium) — Sodium 0.5 does not
  call the Fabric Renderer API, so without Indium blocks show the default texture.

## Сборка / Building

```bash
./gradlew build   # jar в build/libs/
```

## Лицензия / License

CC0-1.0
