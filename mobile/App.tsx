import { Component, ReactNode } from "react";
import { ActivityIndicator, SafeAreaView, StyleSheet, Text, View } from "react-native";

import { AppBootstrapProvider } from "./src/app/AppBootstrap";
import { useAppBootstrap } from "./src/app/AppBootstrap";
import { colors, spacing, typography } from "./src/theme/tokens";

function AppGate() {
  const { status, logout, currentUser, session } = useAppBootstrap();

  if (status === "loading") {
    return (
      <SafeAreaView style={styles.loadingScreen}>
        <View style={styles.loadingContent}>
          <ActivityIndicator color={colors.blue} />
          <Text style={styles.loadingText}>Loading Hotel OpAI</Text>
        </View>
      </SafeAreaView>
    );
  }

  if (status === "unauthenticated") {
    const { LoginScreen } = require("./src/components/Auth/LoginScreen") as typeof import("./src/components/Auth/LoginScreen");
    return <LoginScreen />;
  }

  const { AssistantHomeScreen } = require("./src/components/Assistant/AssistantHomeScreen") as typeof import("./src/components/Assistant/AssistantHomeScreen");
  return (
    <AssistantHomeScreen
      accessToken={session?.accessToken ?? null}
      currentUser={currentUser}
      onLogout={() => void logout()}
    />
  );
}

export default function App() {
  return (
    <AppErrorBoundary>
      <AppBootstrapProvider>
        <AppGate />
      </AppBootstrapProvider>
    </AppErrorBoundary>
  );
}

type AppErrorBoundaryState = {
  error: Error | null;
};

class AppErrorBoundary extends Component<{ children: ReactNode }, AppErrorBoundaryState> {
  state: AppErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): AppErrorBoundaryState {
    return { error };
  }

  render() {
    if (this.state.error) {
      return (
        <SafeAreaView style={styles.errorScreen}>
          <Text style={styles.errorTitle}>Hotel OpAI failed to load</Text>
          <Text style={styles.errorMessage}>{this.state.error.message}</Text>
        </SafeAreaView>
      );
    }

    return this.props.children;
  }
}

const styles = StyleSheet.create({
  loadingScreen: {
    flex: 1,
    backgroundColor: colors.background,
    alignItems: "center",
    justifyContent: "center"
  },
  loadingContent: {
    alignItems: "center",
    gap: spacing.md
  },
  loadingText: {
    color: colors.textMuted,
    fontSize: typography.subtitle,
    fontWeight: "700"
  },
  errorScreen: {
    flex: 1,
    backgroundColor: colors.background,
    alignItems: "center",
    justifyContent: "center",
    padding: spacing.xl
  },
  errorTitle: {
    color: colors.text,
    fontSize: typography.title,
    fontWeight: "800",
    textAlign: "center"
  },
  errorMessage: {
    marginTop: spacing.md,
    color: colors.textMuted,
    fontSize: typography.body,
    textAlign: "center"
  }
});
