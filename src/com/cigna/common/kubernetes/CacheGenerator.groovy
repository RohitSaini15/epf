package com.cigna.common.kubernetes

import com.cigna.base.DockerPipelineLib

/**
 * Create and Delete Folders to store Cache
 */
class CacheGenerator extends DockerPipelineLib {

    /*
     * Checks if folder exists, if not then creates it.
     * Creation script includes logic to verify the folder is created
     */
    protected void generateFolder(String folderName) {
        //script.sh("mc mb --insecure --ignore-existing minio/\$BUCKET_NAME/${folderName}/")
    }

    /*
     * Used to delete the workspace folder after a pipeline run which
     * will verify the folder within the bucket exists, if it exists the
     * folder will be deleted
     */
    protected void deleteFolder(String folderName) {
        return
        script.container('mc') {
            script.sh(
                "mc rm -q --insecure --force --recursive minio/\$BUCKET_NAME/${folderName} 1>/dev/null || true"
            )
        }
    }
}
