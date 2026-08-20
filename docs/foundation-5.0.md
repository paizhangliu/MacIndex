# MacIndex 5.0 foundation

This document records the foundation that is implemented in MacIndex 5.0. It is a runtime and
maintenance contract, not a future migration plan.

## Product model

MacIndex has two facts with different owners:

1. The machine catalog is one complete, immutable asset bundle shipped in the APK.
2. User settings and private library data are mutable, backed up, and updated atomically.

The Android runtime loads one compiled catalog and one small user-state store. UI layers consume
these two sources without owning either lifecycle.

## Build-time catalog

The authoring source is `catalog/machines/`: one strict TOML document per stable UID. The UID is
the file name and its ascending order is the catalog order; typed fields own introductions,
identifiers, ordered codenames, links, resource keys, and lightly marked rich text. The compiler
derives Java UTF-16 spans from that markup. Python 3.11 or newer compiles these files using only the
standard library.

The deterministic build pipeline is:

```text
catalog/machines/*.toml + taxonomy/resource/compat manifests
    -> Python validation and named-field textproto
    -> pinned protoc 4.34.1 deterministic encoding
    -> catalog.pb + pictures + logos + sounds
    -> APK assets/catalog/
```

The compiler validates the release-content reference closure, including:

- unique stable UIDs and typed author documents;
- taxonomy keys, product groups, dates, search tokens, codenames, links, sound profiles, and support
  values;
- UTF-16 rich-text ranges used by Android;
- machine picture assets;
- processor/graphics logo keys and night treatment;
- released 4.9.1 names, validated against the target machines' search names;
- missing and orphaned catalog-owned runtime resources.

`catalog.pb` contains the UID migration table, typed dates, links, enums, resource keys, text
ranges, and one structured
`{ value, optional qualifier }` entry for every name, alias, codename, and identifier. Display and
search derive from the same authored entry rather than parallel authoring fields. The payload also
contains compiler-generated search projections for finite abbreviations, processor terms,
introduction years, and whitespace-free name mappings. The surrounding asset directory contains
the exact pictures, logos, and audio referenced by that payload.

## Runtime catalog

`CatalogLoader` parses `assets/catalog/catalog.pb` once. Build-time validation owns the reference
closure between that payload and its bundled resources. Runtime constructs only the indexes needed
to use it.
`MachineCatalog` is immutable and process-lived. Its public boundary uses `Machine` and stable UID
only:

- `findByUid` / `requireByUid`
- `browseGroups`
- `scopeMachines`
- `sequenceForProductType`
- `search`
- `resolveLegacyName`

Array indexes may exist inside the implementation but never cross into an Activity, Intent,
saved state, adapter, or user record. Navigation uses `NavigationContract` and UID-only requests.
`resolveLegacyName` serves the one preference migration and public name-based old links by exact
match over the normalized search names already cached in each machine. It does not package the
compatibility JSON, duplicate those names in protobuf, build a second name index, or use substring
search semantics.

Catalog-specific search projections and query vocabulary are compiled into the payload. Android
therefore does not parse processor prose, resolve abbreviation selectors, maintain a second alias
table, or scan every machine to rediscover semantic phrases and Part Number stems. Runtime consumes
the immutable vocabulary, normalizes user input using NFKC, trim, and `Locale.ROOT` lowercase, then
applies the generic matching and relevance algorithm.
The searchable facts are machine names and aliases, machine codenames, model numbers, part
numbers, model identifiers, Gestalt IDs, EMC numbers, and introduction years. Names and official
aliases also have a compiler-generated whitespace-free form, so `macbookpro` and `powermac` retain
normal name-search semantics without becoming authored aliases. Processor projection reads only
the marked model headings and emits supported generation, family, model, and Intel-codename terms;
Apple M-series development codenames, core codenames, frequency, cache, core counts, and unmarked
prose remain excluded. Stable processor abbreviations
are authored in `catalog/search_aliases.json` and include `68K`,
`PPC`, `P4`, `C2D`, `C2E`, the Intel platform forms `NHM`, `WSM`, `SNB`, `IVB`, `HSW`, `BDW`,
`PNR`, `SKL`, `KBL`, `CFL`, `AML`, `CLX`, `CML`, and `ICL`, plus compact Apple-silicon forms
such as `M1Pro` and `M1P`. The same marked-heading projection exposes `T1`, `T2`, `A12Z`, and
`A18 Pro`; `A18Pro` is its compact form.

Machine abbreviations are the other finite exact-token table in `catalog/search_aliases.json`.
Series terms are `MB`, `MBN`, `MBP`, `MBA`,
`MM`, `MP`, `PB`, `PM`, `WGS`, `ANS`, and `DTK`; generation or feature terms are `BW`, `DA`, `QS`,
`MDD`, `WS`, `rMB`, `nMB`, `rMBP`, and `TB`. They participate in the same whitespace-token AND
matching as catalog facts and are not recognized as substrings.

Introduction years are also exact tokens: `2008` can match the 2008 introduction fact, while
fragments such as `20`, `200`, or `08` do not produce year matches. A complete year still composes
normally with another term, as in `macbookpro 2014`.

The complete query phrase and whitespace-token AND forms are both evaluated. Adjacent query words that
already form a complete name, codename, or processor phrase in the catalog are also evaluated
together. Processor names such as `M1 Pro` remain atomic; other phrases retain the distributed AND
alternative. Tokens may be satisfied by different fields of the same machine, but otherwise equal
results prefer an adjacent phrase and then prefer more query terms supported by one displayed
catalog value. Every query length and searchable field follows the same contains-matching rule;
relevance ranking, rather than recall exceptions, places complete units and stronger coverage
first. Ordinary ASCII, full-width, and CJK punctuation separates words; slash and hyphen remain
searchable syntax, and a comma between digits remains part of an identifier such as
`MacBookPro8,1`. Letter-to-digit transitions inside values such as `J313` or `MD313` are not
semantic boundaries. Every machine appears at most once, while its `SearchHit` may carry several
pieces of evidence so the UI can explain and highlight a cross-field result.

Searchable Part Number data stores the actual stem and known revisions. Details render each family
as `STEM*/REV`, while search accepts the stem, a one- or two-letter region prefix, an optional slash,
and a supported revision. No country-code list is required. Once a query continues past a known
stem, it is interpreted as a Part Number and malformed suffixes return no results. The bare stem
still keeps exact matches from the other identifier fields visible.

Ranking compares the quality of the textual evidence: complete semantic units precede prefixes
and interior matches, then query coverage and the position inside that unit are considered. When
the textual evidence is otherwise equal, a visible canonical-name explanation is preferred over a
codename or hidden identifier explanation. True ties use the homepage category order,
then the earliest introduction, then the visible machine name's
locale-independent natural order and UID. Catalog order and field-enum order never participate.
These relevance rules remain client behavior: the Catalog supplies typed evidence, not weights,
ranking configuration, or executable rules.

The search page updates its one continuous list as the user types. If one query matches at least
two fields, temporary `All` and field chips let the user refine only that current query; they are not saved as an application
preference and never silently hide results. A canonical-name match is
bolded directly in the title. Other evidence is shown as `Field: value`, with only the matched
portion bolded and no unrelated date. A one-result search remains a normal row that the user
chooses to open. Previous/next navigation follows the displayed result order unless fixed family
navigation is enabled.

Browse order and fixed product navigation are produced by the catalog itself. Random selection
uses `scopeMachines`, where every UID occurs exactly once and presentation grouping cannot alter
its probability.

## Catalog and resources

The bundled Catalog owns its parameters, machine pictures, catalog logos, and sounds together.
Machine records contain their resolved media file keys and logo night treatment; UI code does not
maintain a second Android-resource switch or registry. Static application artwork such as the app
logo and About-page logos remains in the APK because it is not catalog data.

Machine images are decoded off the main thread by `LifecycleMachineImageLoader`. Requests are
latest-wins per view, stale results are recycled, and cancellation cannot strand an unowned
bitmap. Bitmap and MediaPlayer instances never enter persistent state or a ViewModel.

Catalog content changes ship with an app release. There is no independent Catalog version,
installer, download state, activation state, hash registry, or rollback copy.

## User state

`AppStateRepository` is the sole owner of one Proto DataStore. The root state contains:

- typed preferences;
- remembered main-page UI choices;
- comments;
- favourite folders with monotonic `folderId` and `nextFolderId`;
- compare list and selection;
- skipped update version;
- the last registered app version code;
- one durable pending notice.

Appearance is the single intentional exception. `ThemeBootstrapStore` is its only source so the
theme can be chosen synchronously before the first frame. Appearance is not duplicated in Proto or
included in user-data export.

UI code cannot access protobuf builders or a generic edit block. It submits semantic commands such
as `setComment`, `createFavouriteFolder`, `setFavouriteMembership`, or `setCompareSelection`.

Confirmed commands enter one process-owned FIFO lane. Each command performs one DataStore
`updateData`; a failed command does not kill or reorder successors. Activity destruction may cancel
its result callback but cannot cancel an already confirmed durable write. This preserves user
intent order and prevents lifecycle-driven data loss.

Import application and export snapshot acquisition use the same lane:

- export observes every previously confirmed command;
- import assigns fresh folder IDs from the current monotonic counter and atomically replaces the
  three library domains;
- an old in-flight folder ID can never alias a newly imported folder.

The external transfer format is one UTF-8 JSON schema-1 envelope. Invalid size, syntax, key,
type, or UID data rejects the entire file without writing. Valid missing UIDs are
removed in the prepared preview; the confirmation reports the count once, and importing does not
repeat the same information as a later notice.

## 4.9.1 migration

There is one public migration: the released 4.9.1 SharedPreferences format to 5.0 Proto.

- The converter reads only fields released in the dedicated 4.9.1 preference file; any unknown
  residue is cleaned but never becomes part of 5.0 state.
- Comments, favourite folders, compare ordering, and selections preserve their original order.
- released machine names resolve exactly through the catalog's existing search names.
- the released `lastKnownVersion` value becomes the registered app version code.
- the ambiguous PowerBook Duo Dock Series record is removed and reported.
- a malformed user-data class is discarded as a class; other classes remain intact.
- Proto persistence succeeds before legacy keys are cleaned, so interruption retries safely.
- an already non-default Proto state is never overwritten by a restored legacy preference file.
- corruption follows the one Proto reset path; it does not activate a second legacy-recovery
  matrix.

## Startup and ownership

`MacIndexApplication` is the composition root. It owns:

- `ThemeBootstrapStore`;
- `AppStartup`;
- `VolumeWarningSession`;
- the process-scoped automatic update coordinator;
- global system-bar policy.

`AppStartup` publishes only `Loading`, `Ready(catalog, repository)`, or typed `Fatal`. It loads the
bundled Catalog, opens or migrates user state, and registers the current app version. If that
version has not been registered before, the same atomic DataStore update reconciles comments,
favourites, and compare UIDs through the Catalog's retirement table and then records the version.
Failure cannot advance the registered version, so the operation retries on the next launch.
Missing UIDs without a replacement are removed and reported through the existing saved-data
notice. A previously registered version is not reconciled again. Only after these steps succeed
does startup publish Ready. AndroidX SplashScreen holds Main content during this startup.

The UID table is cumulative and every replacement points directly to a machine active in the
bundled Catalog, so an app may skip releases without a chained migration runner. The same resolver
is used when importing an older user-data export: replaced UIDs move to their current machine,
while permanently retired or unknown UIDs follow the existing removal preview.

Current Java Activities use a thin lifecycle adapter. Pages that do not need catalog data do not
wait for it. There is no blocking `requireReady`, Activity-owned recovery loop, static
`MainActivity` service locator, or second catalog owner.

External shortcuts and app links remain in the current Intent until Ready and are consumed once.
Saved consumed markers prevent process-restored Activity records from replaying an old request, but
a newly delivered actionable Intent always overrides the old marker.
New share links contain stable UIDs. Name-based links emitted by 4.9.1 remain readable through the
same exact compatibility resolver used during preference migration.

## UI state and rendering

The retained Views/multi-Activity UI is an adapter over the final foundation, not part of it.

- Main, Search, Favourite, Comment, Compare, Specs, and ViewImage hold UID or immutable Machine.
- Search saves its query and temporary field scope across Activity recreation, then synchronously
  recomputes the live unified results.
- Favourite and Comment render directly from the latest immutable user state. Lifecycle replay of
  an unchanged state is suppressed by the shared lifecycle adapter.
- Compare separates its structural/media render key from highlight state. Highlight changes only
  recolor existing rows. Returning to the page rebinds released audio without rebuilding rows or
  decoding images; a real pair change performs one structural render.
- media release is independent per player, so one invalid player cannot prevent another from
  relinquishing resources.

Only genuine long-lived work has a state owner:

- process-scoped AppStartup;
- Settings transfer workflow across configuration changes;
- manual update ViewModel;

Specs and Compare do not have ViewModels. Confirmed durable changes belong to the process-scoped
repository; dialog sequencing and other follow-up UI actions belong only to the current Activity
instance and are not replayed after recreation.

Automatic update checking is process-owned because its once-per-process gate, request, and
unconsumed result must have the same lifetime. A Main Activity may disappear while the request
continues; a later Main observes the retained result. Manual checks remain page-owned and retryable.

## Error boundaries

Failures are classified by provenance rather than guessed from an arbitrary Throwable type:

1. Catalog asset I/O or format failure is fatal release-content failure; user state is untouched.
2. Proto corruption takes the one documented reset path and persists a notice. Ordinary user-state
   I/O failure does not clear data and is presented as an operation failure.
3. Invalid import/share/network/browser/document-provider input is expected and never contaminates
   startup or user state.
4. Cancellation is silent.
5. Unknown program invariants are not converted into expected failures.

Expected failures are caught at the operation that understands them. If the user needs to make a
decision or the requested action could not finish, that operation provides its own dialog, inline
validation, or brief message. Failures in optional background work or resource cleanup that do not
require user action are written to Logcat only. There is no catch-all UI boundary and no shared
generic wording for unrelated tasks. Durable user-state commands record their expected failure at
the repository boundary as well, so destroying the initiating screen cannot make the failure
silent.

Unexpected exceptions remain uncaught so Android terminates the unsafe process normally. A minimal
default uncaught-exception handler synchronously writes one bounded diagnostic report to no-backup
private storage, then always delegates to Android's original handler. It does not display UI,
restart the process, access user state, or attempt recovery during the crash. After a later
successful startup, the main screen presents the report before external navigation, pending
notices, or automatic-update UI. Rotation does not acknowledge it; MacIndex deletes it only when
the user continues.
Coroutine cancellation remains silent and is rethrown where required. VM failures such as
`OutOfMemoryError` are never treated as recoverable application errors.

## Platform chrome

`SystemBarController` applies opt-in system-bar chrome from the Application lifecycle, including
third-party OSS license Activities that inherit `AppTheme`. It reapplies after Activity creation so
library edge-to-edge calls cannot overwrite the final chrome. A theme attribute excludes explicit
transparent system Activities.

API 35+ uses AndroidX protection views for the status inset. API 26-28 sets both navigation-bar
background and icon appearance; API 23-25 uses a dark navigation bar with light controls.
`ContentInsetsHelper` is limited to current-app content padding and does not own global chrome.

## Build and verification gates

The principal local gate is:

```text
./gradlew :app:check --no-daemon
```

It includes unit tests, lint, one production catalog compilation, and independent catalog-tool
fixture tests. Device tests validate packaged catalog/resources, actual image/logo/audio decoding,
startup Ready, navigation request precedence, and retained update results.

The local `assembleRelease` artifact is intentionally unsigned. A distributable build must be signed
outside the repository, then pass signature verification and 16 KiB `zipalign`,
and a fresh hash calculation. The minimum-SDK smoke test must run against that signed artifact.

The Gradle daemon uses JDK 21 as required by the current AGP/lint toolchain. App source and target
compatibility remain Java 17. Catalog tools require Python 3.11 or newer and only its standard
library. Production code uses only platform library APIs available on API 23, so core-library
desugaring is not required.
