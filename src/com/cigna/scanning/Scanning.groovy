package com.cigna.scanning

import com.cigna.base.Phase
import com.cigna.common.request.CurlRequestor

/**
 * Abstract base class that defines the contract for scanning
 */
abstract class Scanning extends Phase {
    Scanning() {
        groupID = 'scanning'
    }
    protected CurlRequestor curlRequestor
    protected String buildContainerName

    abstract void scan()

}
