import { useState } from "react";
import { ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { OnboardingPayload } from "../../api/admin/AdminApi";
import { colors, radius, spacing } from "../../theme/tokens";
import {
  emptyOnboardingDraft,
  ONBOARDING_STEPS,
  OnboardingDraft,
  validateOnboardingStep,
} from "./adminLogic";
import { AdminButton, AdminModal, adminStyles } from "./AdminUi";

export function HotelOnboardingWizard({
  visible,
  administratorUserId,
  onClose,
  onCreate,
  busy,
}: {
  visible: boolean;
  administratorUserId: string;
  onClose: () => void;
  onCreate: (payload: OnboardingPayload) => Promise<void>;
  busy: boolean;
}) {
  const [step, setStep] = useState(0),
    [draft, setDraft] = useState<OnboardingDraft>(() =>
      emptyOnboardingDraft(administratorUserId),
    ),
    [entry, setEntry] = useState(""),
    [error, setError] = useState<string | null>(null);
  const errors = validateOnboardingStep(step, draft);
  const close = () => {
    setStep(0);
    setDraft(emptyOnboardingDraft(administratorUserId));
    setEntry("");
    setError(null);
    onClose();
  };
  const next = () => {
    if (errors.length) {
      setError(errors.join("\n"));
      return;
    }
    setError(null);
    setStep((v) => Math.min(ONBOARDING_STEPS.length - 1, v + 1));
  };
  const add = () => {
    try {
      const parts = entry.split("|").map((v) => v.trim());
      if (step === 1)
        setDraft((d) => ({
          ...d,
          departments: [
            ...d.departments,
            { code: required(parts[0]), name: required(parts[1]) },
          ],
        }));
      if (step === 2)
        setDraft((d) => ({
          ...d,
          buildings: [
            ...d.buildings,
            {
              code: required(parts[0]),
              name: required(parts[1]),
              floors: required(parts[2]).split(",").map(Number),
            },
          ],
        }));
      if (step === 3)
        setDraft((d) => ({
          ...d,
          rooms: [
            ...d.rooms,
            {
              buildingCode: required(parts[0]),
              floorNumber: Number(required(parts[1])),
              roomNumber: required(parts[2]),
              roomType: parts[3] ?? "",
            },
          ],
        }));
      if (step === 5)
        setDraft((d) => ({
          ...d,
          skills: [
            ...d.skills,
            {
              code: required(parts[0]),
              name: required(parts[1]),
              description: parts[2] ?? "",
            },
          ],
        }));
      if (step === 7)
        setDraft((d) => ({
          ...d,
          shifts: [
            ...d.shifts,
            {
              code: required(parts[0]),
              name: required(parts[1]),
              startTime: required(parts[2]),
              endTime: required(parts[3]),
            },
          ],
        }));
      setEntry("");
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Invalid entry");
    }
  };
  const submit = async () => {
    const all = ONBOARDING_STEPS.flatMap((_, i) =>
      validateOnboardingStep(i, draft),
    );
    if (all.length) {
      setError(all[0] ?? "Onboarding data is invalid");
      return;
    }
    try {
      await onCreate({
        ...draft,
        address: draft.address || undefined,
        rooms: draft.rooms.map((r) => ({
          ...r,
          roomType: r.roomType || undefined,
        })),
        skills: draft.skills.map((s) => ({
          ...s,
          description: s.description || undefined,
        })),
      });
      close();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Hotel onboarding failed");
    }
  };
  return (
    <AdminModal visible={visible} title="Hotel onboarding" onClose={close}>
      <View style={styles.steps}>
        {ONBOARDING_STEPS.map((name, index) => (
          <View
            key={name}
            style={[
              styles.step,
              index === step && styles.current,
              index < step && styles.done,
            ]}
          >
            <Text style={styles.stepText}>
              {index < step ? "✓ " : ""}
              {name}
            </Text>
          </View>
        ))}
      </View>
      <ScrollView
        style={styles.scroll}
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={styles.body}
      >
        {step === 0 ? (
          <>
            <Field
              label="Hotel name"
              value={draft.name}
              set={(name) => setDraft((d) => ({ ...d, name }))}
            />
            <Field
              label="Hotel code"
              value={draft.code}
              set={(code) =>
                setDraft((d) => ({ ...d, code: code.toUpperCase() }))
              }
            />
            <Field
              label="Timezone"
              value={draft.timezone}
              set={(timezone) => setDraft((d) => ({ ...d, timezone }))}
            />
            <Field
              label="Address (optional)"
              value={draft.address}
              set={(address) => setDraft((d) => ({ ...d, address }))}
            />
          </>
        ) : null}
        {[1, 2, 3, 5, 7].includes(step) ? (
          <EntryStep
            step={step}
            entry={entry}
            setEntry={setEntry}
            add={add}
            draft={draft}
          />
        ) : null}
        {step === 4 ? (
          <Text style={adminStyles.help}>
            A hotel-scoped HOTEL_ADMIN role is created transactionally and
            populated from the persisted hotel administration permission
            catalog. Permissions can be refined after onboarding.
          </Text>
        ) : null}
        {step === 6 ? (
          <>
            <Text style={adminStyles.help}>Initial Hotel Administrator</Text>
            <Text style={adminStyles.rowTitle}>Current platform user</Text>
            <Text style={adminStyles.rowDetail}>
              {draft.administratorUserId}
            </Text>
          </>
        ) : null}
        {step === 8 ? <Review draft={draft} /> : null}
        {error ? <Text style={adminStyles.error}>{error}</Text> : null}
      </ScrollView>
      <View style={adminStyles.actions}>
        <AdminButton
          label="Back"
          tone="secondary"
          disabled={step === 0 || busy}
          onPress={() => {
            setError(null);
            setStep((v) => v - 1);
          }}
        />
        {step < 8 ? (
          <AdminButton label="Next" disabled={busy} onPress={next} />
        ) : (
          <AdminButton
            label="Create hotel"
            disabled={busy}
            onPress={() => void submit()}
          />
        )}
      </View>
    </AdminModal>
  );
}
function Field({
  label,
  value,
  set,
}: {
  label: string;
  value: string;
  set: (v: string) => void;
}) {
  return (
    <View>
      <Text style={adminStyles.label}>{label}</Text>
      <TextInput value={value} onChangeText={set} style={adminStyles.input} />
    </View>
  );
}
function EntryStep({
  step,
  entry,
  setEntry,
  add,
  draft,
}: {
  step: number;
  entry: string;
  setEntry: (v: string) => void;
  add: () => void;
  draft: OnboardingDraft;
}) {
  const hints: Record<number, string> = {
    1: "CODE | Name",
    2: "CODE | Name | 0,1,2",
    3: "BUILDING | floor | room | type",
    5: "CODE | Name | Description",
    7: "CODE | Name | 07:00 | 15:00",
  };
  const lists: Record<number, unknown[]> = {
    1: draft.departments,
    2: draft.buildings,
    3: draft.rooms,
    5: draft.skills,
    7: draft.shifts,
  };
  return (
    <>
      <Text style={adminStyles.help}>
        Add one entry at a time: {hints[step] ?? "Complete the entry"}
      </Text>
      <TextInput
        value={entry}
        onChangeText={setEntry}
        placeholder={hints[step] ?? "Entry"}
        style={adminStyles.input}
      />
      <AdminButton label="Add" tone="secondary" onPress={add} />
      {(lists[step] ?? []).map((item, index) => (
        <Text key={index} style={adminStyles.rowDetail}>
          ✓{" "}
          {Object.values(item as Record<string, unknown>)
            .map(String)
            .join(" · ")}
        </Text>
      ))}
    </>
  );
}
function Review({ draft }: { draft: OnboardingDraft }) {
  return (
    <View>
      <Text style={adminStyles.rowTitle}>
        {draft.name} · {draft.code}
      </Text>
      <Text style={adminStyles.rowDetail}>
        {draft.timezone}
        {draft.address ? ` · ${draft.address}` : ""}
      </Text>
      <Text style={adminStyles.rowDetail}>
        {draft.departments.length} departments · {draft.buildings.length}{" "}
        buildings · {draft.rooms.length} rooms
      </Text>
      <Text style={adminStyles.rowDetail}>
        {draft.skills.length} skills · {draft.shifts.length} shifts · 1 initial
        administrator
      </Text>
      <Text style={adminStyles.help}>
        Create is one backend transaction. Any validation failure rolls the
        entire hotel setup back.
      </Text>
    </View>
  );
}
function required(v: string | undefined) {
  if (!v) throw new Error("Complete all required entry fields");
  return v;
}
const styles = StyleSheet.create({
  steps: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 4,
    marginBottom: spacing.md,
  },
  step: {
    borderRadius: radius.pill,
    backgroundColor: colors.surfaceMuted,
    paddingHorizontal: 7,
    paddingVertical: 4,
  },
  current: {
    backgroundColor: colors.greenSoft,
    borderWidth: 1,
    borderColor: colors.greenBorder,
  },
  done: { opacity: 0.7 },
  stepText: { fontSize: 9, fontWeight: "800", color: colors.nav },
  scroll: { flexShrink: 1, minHeight: 0 },
  body: { gap: spacing.sm, paddingBottom: spacing.md },
});
