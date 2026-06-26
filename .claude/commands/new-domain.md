Create a complete new UI domain called $ARGUMENTS in marketplace-app following the established patterns.

## Reference implementations

- View: `AdvertisementsView`, `UserView`
- Overlay: `AdvertisementOverlay`
- ViewModeHandler: `AdvertisementViewOverlayModeHandler`
- FormModeHandler: `UserFormOverlayModeHandler`
- QueryBlock: `AdvertisementQueryBlock`, `UserQueryBlock`
- FilterMeta / SortMeta: `AdvertisementFilterMeta`, `AdvertisementSortMeta`

## Package structure

```
ui/views/main/tabs/$ARGUMENTS_LOWER/
├── $NAMEView.java
├── $NAMEOverlay.java
├── card/ or grid/
│   └── $NAMECardView.java or $NAMEGridConfigurator.java
├── overlay/
│   ├── modes/
│   │   ├── $NAMEViewOverlayModeHandler.java
│   │   └── $NAMEFormOverlayModeHandler.java
│   └── elements/
└── query/
    ├── $NAMEQueryBlock.java
    ├── $NAMEFilterMeta.java
    ├── $NAMESortMeta.java
    └── $NAMEQueryConfig.java
```

## Rules per class

**$NAMEView:**
- `extends VerticalLayout`, `@SpringComponent @UIScope @RequiredArgsConstructor @Slf4j`
- `init()` — `protected`, order: CSS → component → wrapper → add() → subscriptions → register → refresh()
- `refresh()` — `private`, always with try/catch/finally { queryStatusBar.update() }
- `@PreDestroy destroy()` — only if settingsPaginationBinding is present

**$NAMEOverlay:**
- `extends AbstractEntityOverlay`, holds `private OverlaySession session`
- `switchTo()` — first line: `currentFormHandler = null`
- `openForView` / `openForEdit` / `openForCreate` → `launchSession(this::switchTo)`
- `switchToEdit` / `doCancel` → `session = session.toXxx(); switchTo()` (not launchSession!)
- `afterSave` EDIT: `currentFormHandler.afterSave(true); session = session.withEntity(fresh)`
- `afterSave` CREATE: `closeToList()`
- `doCancel`: `currentFormHandler.discardChanges();` then switchTo or closeToList

**$NAMEViewOverlayModeHandler:**
- `extends AbstractViewOverlayModeHandler`, implements `Configurable<T, Parameters>`
- Implements: `tabsCssClass()`, `buildPrimaryTab()`, `buildPrimaryContent()`,
  `buildSecondaryTab()` (return null for single-tab layout), `buildHeaderActions()`

**$NAMEFormOverlayModeHandler:**
- `extends AbstractFormOverlayModeHandler`, implements `Configurable<T, Parameters>`
- `activate()` delegates to `buildBinder(dto)` for binder logic
- Tabs via `buildTabbedContent(...)` from base class
- Public `discardChanges()` for form reset

**$NAMEQueryBlock:**
- `@UIScope`, `initLayout()` — private `@PostConstruct`
- Registration: `sortProcessor.register(SortMeta.FIELD, icon, actionBlock)`
- Registration: `filterProcessor.register(FilterMeta.FIELD, field, actionBlock)`

**$NAMEFilterMeta / $NAMESortMeta:**
- `@NoArgsConstructor(access = AccessLevel.PRIVATE)`
- `SortFieldMeta.of(EntityDto.Fields.fieldName, I18nKey)` — always Fields.*, never raw strings

**$NAMEQueryConfig:**
- `@Bean @UIScope FilterProcessor<FilterDto>`
- `@Bean("${name}SortProcessor") @UIScope SortProcessor` with CustomSort
- `@Bean @Scope("prototype") QueryStatusBar<FilterDto>`

## SPI integration

If a port exists:
- View uses `ComponentFactory<$NAMEPort>` with `.findIfAvailable()`
- Overlay and handlers inject the port via `@RequiredArgsConstructor`

## After generation

1. Register new view in MainView tabs
2. Add i18n keys to `I18nKey` enum
3. Add translations to `messages_en.properties` and `messages_uk.properties`
4. Verify compilation: `mvn compile -pl marketplace-app`
