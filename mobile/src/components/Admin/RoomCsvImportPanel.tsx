import { useMemo, useState } from "react";
import { Platform, ScrollView, Text, TextInput, View } from "react-native";
import * as DocumentPicker from "expo-document-picker";
import * as FileSystem from "expo-file-system";
import { RoomImportResultDto } from "../../api/admin/AdminApi";
import { canConfirmCsvImport, parseRoomCsv, selectCsvFile } from "./adminLogic";
import { AdminButton, AdminCard, adminStyles } from "./AdminUi";

export function RoomCsvImportPanel({
  onImport,
  busy,
}: {
  onImport: (csv: string) => Promise<RoomImportResultDto>;
  busy: boolean;
}) {
  const [csv, setCsv] = useState("building,floor,roomNumber,roomType\n"),
    [fileName, setFileName] = useState<string | null>(null),
    [result, setResult] = useState<RoomImportResultDto | null>(null),
    [confirmed, setConfirmed] = useState(false),
    [error, setError] = useState<string | null>(null);
  const preview = useMemo(() => parseRoomCsv(csv), [csv]);
  const selectFile = async () => {
    setError(null);
    const picked = await DocumentPicker.getDocumentAsync({
      type: ["text/csv", "text/comma-separated-values", "text/plain"],
      copyToCacheDirectory: true,
    });
    if (picked.canceled) return;
    const asset = picked.assets[0];
    if (!asset) return;
    try {
      let content: string;
      if (Platform.OS === "web" && asset.file)
        content = await asset.file.text();
      else content = await FileSystem.readAsStringAsync(asset.uri);
      const selection=selectCsvFile(asset.name,content);
      setCsv(selection.csv);
      setFileName(selection.fileName);
      setResult(null);
      setConfirmed(selection.confirmed);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "The CSV file could not be read",
      );
    }
  };
  const run = async () => {
    setError(null);
    try {
      setResult(await onImport(csv));
      setConfirmed(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Import failed");
    }
  };
  return (
    <AdminCard title="Room CSV import">
      <Text style={adminStyles.help}>
        Select a file or paste the existing building,floor,roomNumber,roomType
        format. Selection never imports automatically.
      </Text>
      <View style={adminStyles.actions}>
        <AdminButton
          label="Select CSV file"
          tone="secondary"
          onPress={() => void selectFile()}
        />
        {fileName ? (
          <Text style={adminStyles.rowDetail}>{fileName}</Text>
        ) : null}
      </View>
      <TextInput
        multiline
        value={csv}
        onChangeText={(value) => {
          setCsv(value);
          setFileName(null);
          setResult(null);
          setConfirmed(false);
        }}
        style={[
          adminStyles.input,
          { minHeight: 110, textAlignVertical: "top", fontFamily: "monospace" },
        ]}
      />
      <Text style={adminStyles.rowDetail}>
        {preview.rows.length} rows · {preview.validCount} locally valid ·{" "}
        {preview.invalidCount} invalid
      </Text>
      {!preview.validHeader ? (
        <Text style={adminStyles.error}>
          Header must be building,floor,roomNumber,roomType
        </Text>
      ) : null}
      <ScrollView style={{ maxHeight: 120 }}>
        {preview.rows
          .filter((r) => !r.valid)
          .slice(0, 20)
          .map((row) => (
            <Text key={row.line} style={adminStyles.error}>
              Line {row.line}: {row.errors.join(", ")}
            </Text>
          ))}
      </ScrollView>
      {confirmed ? (
        <View>
          <Text style={adminStyles.label}>
            Confirm import of {preview.validCount} valid rows?
          </Text>
          <View style={adminStyles.actions}>
            <AdminButton
              label="Import now"
              disabled={busy}
              onPress={() => void run()}
            />
            <AdminButton
              label="Cancel"
              tone="secondary"
              onPress={() => setConfirmed(false)}
            />
          </View>
        </View>
      ) : (
        <AdminButton
          label="Review & confirm"
          disabled={!canConfirmCsvImport(preview) || busy}
          onPress={() => setConfirmed(true)}
        />
      )}
      {error ? <Text style={adminStyles.error}>{error}</Text> : null}
      {result ? (
        <Text style={adminStyles.rowDetail}>
          {result.processed} processed · {result.imported} imported ·{" "}
          {result.duplicates} duplicates · {result.invalid} invalid
        </Text>
      ) : null}
    </AdminCard>
  );
}
