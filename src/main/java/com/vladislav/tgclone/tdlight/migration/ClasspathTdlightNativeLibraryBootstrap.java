package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.TdlightRuntimeConfiguration;
import com.vladislav.tgclone.tdlight.condition.ConditionalOnTdlightRealMode;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnTdlightRealMode
public class ClasspathTdlightNativeLibraryBootstrap implements TdlightNativeLibraryBootstrap {

    private static final Object MONITOR = new Object();
    private static volatile String loadedLibraryPath;

    @Override
    public NativeRuntimeDescriptor ensureLoaded(TdlightRuntimeConfiguration runtimeConfiguration) {
        synchronized (MONITOR) {
            if (loadedLibraryPath == null) {
                loadedLibraryPath = resolveRuntimeDescriptor(runtimeConfiguration);
            }
            initializeTdlightJava(runtimeConfiguration);
            return new NativeRuntimeDescriptor(loadedLibraryPath);
        }
    }

    private String resolveRuntimeDescriptor(TdlightRuntimeConfiguration runtimeConfiguration) {
        String explicitLibraryPath = trimToNull(runtimeConfiguration.libraryPath());
        if (explicitLibraryPath != null) {
            return explicitLibraryPath;
        }
        return resolveNativeResourcePath();
    }

    private void initializeTdlightJava(TdlightRuntimeConfiguration runtimeConfiguration) {
        try {
            Path nativeDirectory = resolveNativeDirectory(runtimeConfiguration);
            Files.createDirectories(nativeDirectory);
            System.setProperty("tdlight.native.workdir", nativeDirectory.toAbsolutePath().toString());
            Class<?> initClass = Class.forName("it.tdlight.Init");
            Method initMethod = initClass.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to initialize TDLight Java bindings after loading native library", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare TDLight native workdir", exception);
        }
    }

    private Path resolveNativeDirectory(TdlightRuntimeConfiguration runtimeConfiguration) {
        String workdir = trimToNull(runtimeConfiguration.nativeWorkdir());
        if (workdir != null) {
            return Path.of(workdir);
        }
        try {
            return Files.createTempDirectory("tdlight-native");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create temporary directory for TDLight native library", exception);
        }
    }

    private String resolveNativeResourcePath() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        List<String> candidates;
        if (osName.contains("mac")) {
            candidates = arch.contains("aarch64") || arch.contains("arm64")
                ? List.of("META-INF/tdlightjni/libtdjni.macos_arm64.dylib")
                : List.of("META-INF/tdlightjni/libtdjni.macos_amd64.dylib");
        } else if (osName.contains("linux") && (arch.contains("amd64") || arch.contains("x86_64"))) {
            candidates = List.of(
                "META-INF/tdlightjni/libtdjni.linux_amd64_gnu_ssl3.so",
                "META-INF/tdlightjni/libtdjni.linux_amd64_gnu_ssl1.so"
            );
        } else {
            candidates = List.of();
        }
        return candidates.stream()
            .filter(candidate -> requireClass().getClassLoader().getResource(candidate) != null)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No supported TDLight native library found for os=" + osName + ", arch=" + arch
            ));
    }

    private Class<?> requireClass() {
        return ClasspathTdlightNativeLibraryBootstrap.class;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
