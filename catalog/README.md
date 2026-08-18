# Catalog authoring source

`machines/` is the complete, human-editable MacIndex machine catalog. It contains one TOML file
per machine, named by stable UID (`MIxxxxxx.toml`). The UID comes only from the file name; it is
never duplicated inside the document. Files have a strict field set instead of a per-file schema
or migration protocol.

Catalog tooling requires Python 3.11 or newer and uses only the Python standard library. No Python
environment, package installation, or lock file is needed.

The Android build performs three steps:

1. `generateCatalogTextProto` validates every machine document, taxonomy, compatibility name, and
   referenced asset, then compiles deterministic UTF-8 textproto using named schema fields and
   enums. Machine order is the stable ascending UID file-name order. Each document names only its
   category; taxonomy derives manufacturer, product type, browse membership, and browse order.
2. `generateCatalog` uses the build's pinned `protoc` to deterministically encode
   `build/generated/catalog/assets/catalog.pb`.
3. `generateMachineResourceRegistry` validates catalog-referenced logo and sound resources,
   then generates typed resource IDs and night treatment. Logo artwork is cropped once in the
   source tree and stored in `drawable-nodpi`.

Each machine document owns its typed introduction values, catalog numbers, ordered `codenames`,
links, resource keys, and lightly marked processor/graphics model names. The Codename field is
curated rather than an unqualified nickname dump. It may retain a widely recognized system project,
platform, logic-board, enclosure, or pre-release product name when that name is materially used to
identify the machine; every non-system value states its layer in the structured `qualifier` field.
Machine codenames are authored as `{ value, optional qualifier }`, shown in authored order, and
searchable only by `value`. The UI renders a qualifier in parentheses after its value; qualifier
text is not part of the search index. Processor-layer
codenames, including SoC and core codenames, are written inline in the `processor` prose inside
ASCII double quotes and are not machine codenames or search terms. Values prefer direct
primary/archive evidence and
independent corroboration, but one credible historical source may be sufficient when it maps the
name to this exact machine or configuration. Conflicting sources are resolved by the most specific
technical evidence available; a useful component or platform name is retained with an explicit
layer qualifier rather than deleted merely because it is not a whole-machine project name.
An irreconcilable mapping is omitted until sufficiently specific evidence resolves it.
The compiler removes rich-text markup and emits the corresponding Java UTF-16 spans.
Duplicate authoring lists, delimiter-encoded lists, and raw numeric offsets are not authoring
protocols.

Searchable Order Number sources store the actual stem and its known revisions, for example
`{ value = "M5994", revisions = ["A"] }`. The app renders this as `M5994*/A`; the asterisk explains
the variable one- or two-letter region code but is not itself searchable data. The same stem may
legitimately return more than one machine. Identity arrays contain identifiers only; descriptive
labels such as `BTO/CTO` belong in the corresponding specification text, not in an identity field.

Allocate the next stable UID with:

```shell
python3 tools/catalog_source.py --allocate-uid
```

The command advances `machine_uid_sequence` and prints the new UID. Create the matching TOML file
before compiling the catalog; the validator rejects any machine UID beyond the allocated sequence.

`old_machine_names.json` is the build-only contract for 4.9.1 preferences and public name-based
share links. The compiler requires a non-empty normalized-unique list, requires every UID to exist,
and requires every historical name to remain one of that machine's current search names. Neither
the JSON nor a duplicate legacy-name field is packaged; runtime exact resolution reuses the
validated search names already present in `catalog.pb`.

`tools/tests/fixtures/normalization_golden.json` is the shared test contract for search
normalization. Independent Python and Java tests validate NFKC, Java `String.trim()` boundary
semantics, and locale-independent lowercase against those cases; it is not a production compiler
input.

`tools/tests/fixtures/text_range_golden.json` tests marked rich-text plain output and Java UTF-16
offsets, including non-BMP input. It is not required to generate the production catalog.

`taxonomy.json` is the only product/manufacturer/browse-order authority.
`resource_manifest.json` is the only semantic catalog logo/sound registry. Unrelated UI resources
remain owned by the Android resource tree and are outside the catalog pipeline.

Search normalization, browse membership, sorting, and navigation sequences are pure derived data.
They are generated from the author documents and manifests and have no separately maintained
authoring copy. Protobuf stores each name, alias, codename, and identifier once as a structured
`{ value, optional qualifier }` entry; display text and normalized search text derive from that same
entry rather than separate display and search fields.
