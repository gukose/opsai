import { useState } from "react";
import {
  ActivityIndicator,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View
} from "react-native";

import { getAppApiErrorMessage } from "../../api/client/AppApiError";
import { colors, radius, spacing, shadow, typography } from "../../theme/tokens";
import { useAppBootstrap } from "../../app/AppBootstrap";

const DEFAULT_HOTEL_CODE = "hotel-opai-demo";
const DEFAULT_EMAIL = "admin@hotelopai.local";
const DEFAULT_PASSWORD = "admin123";

export function LoginScreen() {
  const { login } = useAppBootstrap();
  const [hotelCode, setHotelCode] = useState(DEFAULT_HOTEL_CODE);
  const [email, setEmail] = useState(DEFAULT_EMAIL);
  const [password, setPassword] = useState(DEFAULT_PASSWORD);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const submit = async () => {
    if (isSubmitting) {
      return;
    }

    setIsSubmitting(true);
    setErrorMessage(null);

    try {
      await login({
        hotelCode,
        email,
        password
      });
    } catch (error) {
      setErrorMessage(getAppApiErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.card}>
          <Text style={styles.title}>Hotel OpAI</Text>
          <Text style={styles.subtitle}>Sign in to continue</Text>

          <View style={styles.field}>
            <Text style={styles.label}>Hotel code</Text>
            <TextInput
              autoCapitalize="none"
              autoCorrect={false}
              onChangeText={setHotelCode}
              placeholder="hotel-opai-demo"
              placeholderTextColor={colors.textSubtle}
              style={styles.input}
              value={hotelCode}
            />
          </View>

          <View style={styles.field}>
            <Text style={styles.label}>Email</Text>
            <TextInput
              autoCapitalize="none"
              autoCorrect={false}
              keyboardType="email-address"
              onChangeText={setEmail}
              placeholder="admin@hotelopai.local"
              placeholderTextColor={colors.textSubtle}
              style={styles.input}
              value={email}
            />
          </View>

          <View style={styles.field}>
            <Text style={styles.label}>Password</Text>
            <TextInput
              autoCapitalize="none"
              autoCorrect={false}
              onChangeText={setPassword}
              placeholder="admin123"
              placeholderTextColor={colors.textSubtle}
              secureTextEntry
              style={styles.input}
              value={password}
            />
          </View>

          {errorMessage ? <Text style={styles.error}>{errorMessage}</Text> : null}

          <Pressable
            accessibilityRole="button"
            onPress={submit}
            style={({ pressed }) => [
              styles.button,
              pressed && !isSubmitting ? styles.buttonPressed : null,
              isSubmitting ? styles.buttonDisabled : null
            ]}
          >
            {isSubmitting ? (
              <ActivityIndicator color="#ffffff" />
            ) : (
              <Text style={styles.buttonText}>Sign in</Text>
            )}
          </Pressable>

          <Text style={styles.help}>
            Use the local dev account to verify backend auth and session restore.
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: colors.background
  },
  content: {
    flexGrow: 1,
    justifyContent: "center",
    padding: spacing.xl
  },
  card: {
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.xl,
    backgroundColor: colors.surface,
    padding: spacing.xl,
    ...shadow.card
  },
  title: {
    color: colors.text,
    fontSize: 24,
    fontWeight: "800"
  },
  subtitle: {
    marginTop: 4,
    color: colors.textMuted,
    fontSize: typography.subtitle,
    fontWeight: "600"
  },
  field: {
    marginTop: spacing.lg
  },
  label: {
    marginBottom: spacing.xxs,
    color: colors.textMuted,
    fontSize: typography.caption,
    fontWeight: "700",
    textTransform: "uppercase"
  },
  input: {
    minHeight: 44,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    backgroundColor: colors.surfaceMuted,
    color: colors.text,
    fontSize: typography.body,
    fontWeight: "600"
  },
  error: {
    marginTop: spacing.lg,
    color: colors.red,
    fontSize: typography.caption,
    fontWeight: "700"
  },
  button: {
    marginTop: spacing.xl,
    minHeight: 44,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: radius.md,
    backgroundColor: colors.blue
  },
  buttonPressed: {
    opacity: 0.88
  },
  buttonDisabled: {
    opacity: 0.7
  },
  buttonText: {
    color: "#ffffff",
    fontSize: typography.body,
    fontWeight: "800"
  },
  help: {
    marginTop: spacing.lg,
    color: colors.textSubtle,
    fontSize: typography.caption,
    lineHeight: 14
  }
});
