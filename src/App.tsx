export default function App() {
  const files = [
    "settings.gradle.kts",
    "build.gradle.kts",
    "gradle.properties",
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/zelsignals/app/MainActivity.kt",
    "app/src/main/java/com/zelsignals/app/SignalsViewModel.kt",
    "app/src/main/java/com/zelsignals/app/data/ApiService.kt",
    "app/src/main/java/com/zelsignals/app/data/Models.kt",
    "app/src/main/java/com/zelsignals/app/data/StrategyEngine.kt",
    ".github/workflows/build-apk.yml",
  ];
  return (
    <div className="min-h-screen bg-[#0B0E14] text-slate-200 p-8 font-mono">
      <h1 className="text-2xl font-black tracking-widest text-emerald-400">ZEL SIGNALS PRO</h1>
      <p className="mt-2 text-slate-400">
        Native Android (Kotlin + Jetpack Compose) project. Build the APK via the GitHub Actions workflow.
      </p>
      <ul className="mt-6 space-y-1 text-sm">
        {files.map((f) => (
          <li key={f} className="text-slate-300">• {f}</li>
        ))}
      </ul>
    </div>
  );
}
