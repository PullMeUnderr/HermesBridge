package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.TdlightRuntimeConfiguration;
import com.vladislav.tgclone.tdlight.condition.ConditionalOnTdlightRealMode;
import java.lang.reflect.Method;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnTdlightRealMode
public class NoopTdlightNativeLibraryBootstrap implements TdlightNativeLibraryBootstrap {

    @Override
    public NativeRuntimeDescriptor ensureLoaded(TdlightRuntimeConfiguration runtimeConfiguration) {
        try {
            if (runtimeConfiguration.nativeWorkdir() != null && !runtimeConfiguration.nativeWorkdir().isBlank()) {
                System.setProperty("it.tdlight.native.workdir", runtimeConfiguration.nativeWorkdir());
            }
            Class<?> initClass = Class.forName("it.tdlight.Init");
            Method initMethod = initClass.getMethod("init");
            initMethod.invoke(null);
            Class<?> constructorDetectorClass = Class.forName("it.tdlight.ConstructorDetector");
            Method constructorDetectorInitMethod = constructorDetectorClass.getDeclaredMethod("init");
            constructorDetectorInitMethod.setAccessible(true);
            constructorDetectorInitMethod.invoke(null);
            return new NativeRuntimeDescriptor(runtimeConfiguration.libraryPath());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(
                "TDLight Java classes are not on the classpath. Add the tdlight-java dependency and a tdlight-natives "
                    + "profile such as tdlight-native-macos-arm64 before using REAL mode.",
                exception
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                "Failed to initialize TDLight native runtime via it.tdlight.Init.init(). Check tdlight-natives "
                    + "classifier, OpenSSL requirements, and app.tdlight.native-workdir.",
                exception
            );
        }
    }
}
