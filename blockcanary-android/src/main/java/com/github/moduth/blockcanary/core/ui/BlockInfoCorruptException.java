package com.github.moduth.blockcanary.core.ui;

import java.util.Locale;

/**
 * @author markzhai on 16/8/24
 * @version 1.3.0
 */
class BlockInfoCorruptException extends Exception {

    BlockInfoCorruptException(BlockInfoEx blockInfo) {
        this(String.format(Locale.US,
                "BlockInfo (%s) is corrupt.", blockInfo.logFile.getName()));
    }

    private BlockInfoCorruptException(String detailMessage) {
        super(detailMessage);
    }
}
