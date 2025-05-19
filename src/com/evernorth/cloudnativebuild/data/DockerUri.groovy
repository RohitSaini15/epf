package com.evernorth.cloudnativebuild.data

import com.cloudbees.groovy.cps.NonCPS

class DockerUri {
    /**
     * 'parse' a docker image URI into the image name and the tag. This does not
     * verify/validate the URI, just parses it based on general structure
     * @param uri a 'full' docker URI
     * @return a map with 'image' (which includes everything up to the tag) and 'tag' keys
     */
    @NonCPS
    static Map<String, String> imageAndTag(String uri) {
        def match = uri =~ '(.*/(?:.*/)?.*):(.*)'
        if (match.size() == 1) {
            return [image: match[0][1],
                    tag  : match[0][2]] as Map<String, String>
        } else {
            throw new IllegalArgumentException("$uri does not appear to be a valid docker URI")
        }
    }
}
