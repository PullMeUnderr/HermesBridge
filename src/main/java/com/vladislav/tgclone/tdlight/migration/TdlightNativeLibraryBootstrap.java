package com.vladislav.tgclone.tdlight.migration;

import com.vladislav.tgclone.tdlight.TdlightRuntimeConfiguration;

public interface TdlightNativeLibraryBootstrap {

    NativeRuntimeDescriptor ensureLoaded(TdlightRuntimeConfiguration runtimeConfiguration);

    record NativeRuntimeDescriptor(
        String libraryPath
    ) {
    }
}
