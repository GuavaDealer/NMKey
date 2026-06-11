fix:

```log
[22:12:07 INFO]: [NMKeyTest] Disabling NMKeyTest v1.1.0
[22:12:07 WARN]: Exception in thread "DefaultDispatcher-worker-5" java.lang.IllegalStateException: zip file closed
[22:12:07 WARN]: 	at java.base/java.util.zip.ZipFile.ensureOpen(ZipFile.java:826)
[22:12:07 WARN]: 	at java.base/java.util.zip.ZipFile.getEntry(ZipFile.java:290)
[22:12:07 WARN]: 	at java.base/java.util.jar.JarFile.getEntry(JarFile.java:505)
[22:12:07 WARN]: 	at java.base/java.util.jar.JarFile.getJarEntry(JarFile.java:460)
[22:12:07 WARN]: 	at io.papermc.paper.plugin.entrypoint.classloader.PaperSimplePluginClassLoader.findClass(PaperSimplePluginClassLoader.java:62)
[22:12:07 WARN]: 	at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:557)
[22:12:07 WARN]: 	at io.papermc.paper.plugin.entrypoint.classloader.PaperPluginClassLoader.loadClass(PaperPluginClassLoader.java:118)
[22:12:07 WARN]: 	at io.papermc.paper.plugin.entrypoint.classloader.PaperPluginClassLoader.loadClass(PaperPluginClassLoader.java:107)
[22:12:07 WARN]: 	at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:490)
[22:12:07 WARN]: 	at NMKey-1.1.0-test-plugin.jar//kotlinx.coroutines.internal.LimitedDispatcher$Worker.run(LimitedDispatcher.kt:126)
[22:12:07 WARN]: 	at NMKey-1.1.0-test-plugin.jar//kotlinx.coroutines.scheduling.TaskImpl.run(Tasks.kt:89)
[22:12:07 WARN]: 	at NMKey-1.1.0-test-plugin.jar//kotlinx.coroutines.scheduling.CoroutineScheduler.runSafely(CoroutineScheduler.kt:586)
[22:12:07 WARN]: 	at NMKey-1.1.0-test-plugin.jar//kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.executeTask(CoroutineScheduler.kt:798)
[22:12:07 WARN]: 	at NMKey-1.1.0-test-plugin.jar//kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.runWorker(CoroutineScheduler.kt:717)
[22:12:07 WARN]: 	at NMKey-1.1.0-test-plugin.jar//kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.run(CoroutineScheduler.kt:704)
```
