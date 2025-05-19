package com.evernorth.cloudnativebuild.data

import spock.lang.Specification

class DockerUriSpec extends Specification {

    def "test imageAndTag with valid URI '#uri'"() {
        when:
        def result = DockerUri.imageAndTag(uri)

        then:
        result.image == expectedImage
        result.tag == expectedTag

        where:
        uri                                                         | expectedImage                                   | expectedTag
        'registry.cigna.com/repo/image:tag'                         | 'registry.cigna.com/repo/image'                 | 'tag'
        'registry.cigna.com/repo/subrepo/image:tag'                 | 'registry.cigna.com/repo/subrepo/image'         | 'tag'
        'cigna.jfrog.io/repo/subrepo/image:tag'                     | 'cigna.jfrog.io/repo/subrepo/image'             | 'tag'
        'cigna.jfrog.io/repo.one/subrepo.two/image:tag'             | 'cigna.jfrog.io/repo.one/subrepo.two/image'     | 'tag'
        'repo-one/subrepo-two/image:tag.one'                        | 'repo-one/subrepo-two/image' | 'tag.one'
        'repo/image:tag'                                            | 'repo/image'                 | 'tag'
        'repo/subrepo/image:tag'                                    | 'repo/subrepo/image'         | 'tag'
        'repo/subrepo/another/image:tag'                            | 'repo/subrepo/another/image' | 'tag'
        'docker-dev.artifactory.express-scripts.com/image:tag'      | 'docker-dev.artifactory.express-scripts.com/image'| 'tag'
    }
}