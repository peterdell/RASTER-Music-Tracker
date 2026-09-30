# Plan: WUDSN Base value sets for the Java port's enumerations (V1-V5)

Status: V1 and V2 DONE 2026-10-01 (the container and `AssemblerFormat`,
including the directive attributes); V3-V5 open. The user's
question: what would be required to turn `KeyboardLayout` into a WUDSN
Base value set, so the texts live outside the code in the standard way and
`ValueSetField` can be used.

Decided by the user so far: a WUDSN Base dependency of the model package
is acceptable; an unknown keyboard layout number falls back to QWERTY;
no German texts for now (6.1); the two directive attributes are done
with V2 (6.2); all three enumerations are converted, starting with
`AssemblerFormat` (6.3). Every decision is settled.

## 1. How a WUDSN Base value set works

Verified against the installed `com.wudsn.tools.base` sources
(`repository/ValueSet.java`, `repository/NLS.java`, `gui/ValueSetField.java`)
and the example `com.wudsn.tools.base.atari.Platform`.

- A value set is a `final class X extends ValueSet` whose instances are
  `public static final` fields created in a static block through a private
  `add(id, ...)` helper, kept in a `TreeMap<String, X>`, with
  `getValues()` (unmodifiable list) and `getInstance(String id)`, and
  `initializeClass(X.class, ValueSets.class)` as the block's last
  statement.
- `ValueSet` itself holds `id`, `text` and `sortKey`; `toString()` returns
  the text (that is what a combo box shows), `compareTo` orders by
  `sortKey` first, then by text, case-insensitively. `ValueSet.getValues(
  Class)` calls the class's static `getValues()` **and sorts the result**.
- The texts do **not** come from `X.properties`. `NLS.loadProperties`
  takes the *container* class's name as the path prefix for a `ValueSet`
  subclass, so all value sets of a package share
  `ValueSets.properties` next to a `public class ValueSets extends
  com.wudsn.tools.base.ValueSets` (see `base.atari`'s `ValueSets.java` /
  `ValueSets.properties`). The key of an entry is
  `<SimpleClassName>_<id>`, e.g. `KeyboardLayout_QWERTY=QWERTY Layout`.
  A `_de.properties` beside it gives the German texts (the Atari library
  ships one), and `NLS.initializeLocale` picks the suffix.
- `ValueSetField<T extends ValueSet> extends JComboBox<T>` takes either
  the class (all values, sorted) or a `List<T>`, and offers
  `setValue(T)`/`getValue()` plus an `addActionListener` that suppresses
  the event while `setValue` runs. That last part alone removes a class of
  bugs from the dialogs, which today guard index changes by hand.
- `DataType` has no value set flavour; `ElementFactory.createLabel(
  DataType, JComponent)` works with any component, so the existing label
  code stays as it is. `DataTypes.OptionsDialog_KeyboardLayout` can take
  `KeyboardLayout.class` as its value class instead of `String.class`.

## 2. The three enumerations

### 2.1 `KeyboardLayout` (`model`, since plan 27 three values)

Today `public final class KeyboardLayout` with `int` constants QWERTY 0,
AZERTY 1, QWERTZ 2 and `forLanguage(String)`. The number is the `rmt.ini`
value `KEYBOARD_LAYOUT` shared with `Rmt.exe`, so it must survive as a
number. Users: `Keyboard2NoteMapping.noteKey`, `NoteKeys` (rows, legends,
table, `toQwertyPosition`), `PokeyController.onKeyDown`, `SongInput`,
`RmtOptions`, `OptionsValues`, `RmtConfig`, `RmtCommands.applyOptions`,
`OptionsDialog` (`KEYBOARD_LAYOUTS` array + `JComboBox<String>`), and six
test classes - 14 files.

### 2.2 `TrackerDriverVersion` (`model`, a Java `enum`)

`NONE, UNPATCHED, UNPATCHED_WITH_TUNING, PATCH3, PATCH6, PATCH8, PATCH16,
PATCH_PRINCE_OF_PERSIA` - the C++ `TrackerDriverVersion` enum, its
`ordinal()` the `rmt.ini` value `TRACKERDRIVERVERSION`. The Options
dialog shows **six of the eight** values, in C++'s order
(`DRIVER_VERSIONS`/`DRIVER_VERSION_NAMES` arrays plus an `indexOf`
helper): NONE and UNPATCHED_WITH_TUNING are not offered. The script
command `set driver <name>` parses the enum names, so the ids must stay
`UNPATCHED`, `PATCH3`, ... 21 files use the type, most only as a constant.

### 2.3 `AssemblerFormat` (`model`, a Java `enum`)

`ATASM, XASM`, texts `"Atasm"`/`"Xasm"` in `ExportStrippedRmtDialog.
ASM_FORMATS`, used by that dialog and `ExportRelocatableAsmDialog`
(which borrows the array), by `AsmFileBuilder`/`AsmFileExporter`, the
export settings and the script's `asmformat=` option. The smallest of the
three and a good first one to convert.

## 3. The open question: a subset and an order of values

`ValueSetField` has the two-constructor answer built in: `new
ValueSetField<>(X.class)` takes all values, `new ValueSetField<>(List<X>)`
takes exactly the list given - but it sorts neither, it copies the list
into the model as it is... **except** that the class constructor routes
through `ValueSet.getValues(Class)`, which sorts by `sortKey` then text.
So:

- **Order**: give each instance the `sortKey` that is its display
  position (the `ValueSet(id, text, sortKey)` constructor). With distinct
  sort keys the text never decides, and the class constructor shows the
  intended order. For `TrackerDriverVersion` the sort key is the C++ menu
  order, not the ordinal.
- **Subset**: the list constructor, fed by a static method on the value
  set itself, e.g. `TrackerDriverVersion.getSelectableValues()` -
  the knowledge "NONE is not offered" belongs to the value set, not to the
  dialog. Sorting it stays the value set's job (`ValueSet.sort(list)` is
  public).
- **What is missing in the library**: nothing that blocks this plan, but
  two things would be worth having upstream, and the user owns WUDSN Base:
  1. `ValueSetField(Class, Predicate<T>)` or a `getValues(Class,
     Predicate)` so a subset does not need a hand-written method per case;
  2. a documented rule that `getValues()` returns the declaration order
     and only the field sorts, because today's double sorting (in
     `getValues(Class)` and in nothing else) is easy to miss.
  Both are optional; V5 records them as a suggestion rather than doing
  them, since changing the shared library affects dis6502 and The!Cart
  Studio too.

## 4. What the conversion really touches in the code

Checked line by line on 2026-10-01, after a first draft of this plan had
claimed `switch` statements over these types: **there are none.** What
exists is this, and the distinction decides how much work each batch is.

**Comparisons keep working unchanged.** `AssemblerFormat` is branched on
eight times, always as `assemblerFormat == AssemblerFormat.ATASM` (five
in `AsmFileBuilder`, three in `AsmFileExporter`); `TrackerDriverVersion`
and `KeyboardLayout` are never branched on at all. `ValueSet` defines
`equals` as identity (`this == o`) and its instances are singletons, so
every one of those comparisons compiles and behaves exactly as today. No
mechanical change is needed there.

**Enum-only API must go, six places.** A value set has no `ordinal()`,
`values()` or `name()`:

| Place | Today | After |
|---|---|---|
| `RmtAtariBinaries:29` | the file name `rmt_driver_v` + `ordinal()` + `.obx` | the numeric attribute |
| `RmtConfig:146` | `TrackerDriverVersion.values()` indexed by the ini number | `getInstance(int)` |
| `RmtConfig:208` | writes `ordinal()` | writes the numeric attribute |
| `ScriptRunner:346` | iterates `values()`, matches `name()` | `getValues()`, `getId()` |
| `ExportStrippedRmtDialog:68` | `setSelectedIndex(asmFormat.ordinal())` | `ValueSetField.setValue` |
| `ExportRelocatableAsmDialog:67` | the same | the same |

The driver's file number and its ini number are the same number, so the
one numeric attribute per instance (V3) serves both.

**Instance attributes instead of a comparison** are optional and worth it
in exactly two spots, where the only difference is one string:

- `AsmFileExporter:524` `(format == ATASM) ? "=" : "equ"` ->
  `format.getEqualDirective()`;
- `AsmFileBuilder:35/63/93` `(format == ATASM) ? "* = " + label : "org " +
  label` -> `format.getOriginDirective() + label`, three sites collapsing
  into one expression.

The other five comparisons branch on code structure (whole blocks with
different format strings, `AsmFileBuilder:107/126`, `AsmFileExporter:301/503`)
and stay `if`/`else`.

## 5. Batches

### V1 - the package's value set container - DONE 2026-10-01

`org/atari/raster/rmt/model/ValueSets.java` (`public class ValueSets
extends com.wudsn.tools.base.ValueSets`) and `ValueSets.properties` next
to it (English only, decision 6.1); `pom.xml` already copies `src/java` resources (verify the
`.properties` include pattern, `Actions.properties` is packaged the same
way). No behaviour change yet. A `ValueSetsTest` that loads the container
and asserts every text is non-empty comes with V2.

### V2 - `AssemblerFormat` (the smallest, proves the pattern) - DONE 2026-10-01

The enum becomes a value set with ids `ATASM`/`XASM`, sort keys 0/1, texts
in `ValueSets.properties`. `ExportStrippedRmtDialog` and
`ExportRelocatableAsmDialog` use `ValueSetField<AssemblerFormat>` and
drop `ASM_FORMATS` and the ordinal arithmetic; `ExportSettings`,
`AsmFileBuilder`, `AsmFileExporter` and the script's `asmformat=` keep
working on the instances (`getInstance(id)` for the script). The eight
`==` comparisons stay as they are (section 4); the two directive
spellings become attributes if decision 6.2 says so. Tests: the two
dialog tests, `ScriptRunnerTest`.

### V3 - `TrackerDriverVersion`

Ids as today, sort keys the C++ order, texts the six display names plus
one for `NONE`/`UNPATCHED_WITH_TUNING` (they exist but are not offered).
An `int` attribute per instance for the ini value (the C++ enum's number,
i.e. today's ordinal) with `getInstance(int)`; `RmtConfig` uses it, and an
unknown number keeps today's behaviour. The dialog gets
`ValueSetField<>(TrackerDriverVersion.getSelectableValues())` and loses
`DRIVER_VERSIONS`, `DRIVER_VERSION_NAMES` and `indexOf`. The script's
`parseDriverVersion` iterates `getValues()` instead of `values()` and
matches `getId()`. `RmtAtariBinaries` builds the driver file name from
the same numeric attribute instead of `ordinal()` - the binaries on disk
(`rmt_driver_v0.obx` ...) keep their names, so the numbers must stay what
the ordinals are today.

### V4 - `KeyboardLayout`

Ids `QWERTY`/`AZERTY`/`QWERTZ`, sort keys 0/1/2, an `int` attribute for
the ini value and `getInstance(int)` **falling back to QWERTY** for an
unknown number (the user's decision; today an unknown number plays no
notes at all, see `noteKey`'s `else return -1`). `forLanguage` returns
instances. The 14 users switch from `int` to the type; the three note
tables in `Keyboard2NoteMapping` can hang on the instances as an
attribute (`layout.getNoteKeys()`), which turns `noteKey(vk, layout)` into
a lookup without the if-chain - the same for `NoteKeys`'s rows. The
dialog uses `ValueSetField<KeyboardLayout>` with the class constructor.
`DataTypes.OptionsDialog_KeyboardLayout` gets `KeyboardLayout.class`.

### V5 - documentation and bookkeeping

`plans/README.md`, `NOTES.md`; a paragraph in `CLAUDE.md` or the port plan
on the convention (value sets in the model package, texts in
`ValueSets.properties`, `ValueSetField` in dialogs) so later enumerations
follow it; the two library suggestions of section 3 recorded for WUDSN
Base. No user-visible change at all (decision 6.1: no German texts), so
no `doc/rmt_changes.md` entry.

## 6. Decisions for the user

1. **German texts** (`ValueSets_de.properties`): **DECIDED 2026-10-01 -
   no German texts for now.** Only `ValueSets.properties` is written; the
   port stays English, as its menus, dialogs and messages already are.
   Adding the file later needs no code change, since `NLS` picks the
   locale suffix by itself - so this stays a one-file decision whenever
   the port is to be translated as a whole.
2. **The two directive attributes** of V2: **DECIDED 2026-10-01 - do
   them.** `AssemblerFormat` carries its origin directive (`"* = "` /
   `"org "`) and its assign directive (`"="` / `"equ"`) as attributes;
   the four sites that differ only in that one string
   (`AsmFileBuilder:35/63/93`, `AsmFileExporter:524`) read them instead
   of comparing. The other four comparisons, which branch on code
   structure, stay as they are. The exported files must come out
   byte-identical (`compare_exports.ps1` runs the assembler exports).
3. **Scope**: **DECIDED 2026-10-01 - all three enumerations, starting
   with `AssemblerFormat`.** That is the batch order below (V1 the
   container, then V2 `AssemblerFormat`, V3 `TrackerDriverVersion`, V4
   `KeyboardLayout`): the smallest type first, so the pattern - the
   static block, the properties key, `ValueSetField` in a dialog - is
   settled on two values and two dialogs before the 14- and 21-file
   conversions follow.

## 7. Verification

`mvn -o clean package` per batch; the texts are asserted by a
`ValueSetsTest` (every instance of every value set has a non-empty text
different from its id, which catches a missing properties key - `NLS`
only logs it). `RmtConfigTest` proves the ini numbers unchanged in both
directions, including the QWERTY fallback for an unknown number. The C++
program is not touched, so `compare_exports.ps1` and the action table
must stay identical - that is the real regression net for V3/V4.

## 8. Estimate

V1 small. V2 small-medium. V3 medium (21 files, most trivial). V4 medium
(14 files, the note tables are the interesting part). V5 small. Order: V1,
V2, V3, V4, V5.
