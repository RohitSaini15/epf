package com.evernorth.cloudnativebuild.model


import com.evernorth.cloudnativebuild.model.Credential
import spock.lang.Specification

class CredentialSpec extends Specification{
    def "when toString custom string created without null values"(){
        setup:
        Credential cred = new Credential(id: "foo")
        String expected = "{\"type\":\"usernamePassword\", \"id\":\"foo\"}"
        when:
        String actual = cred.toString()
        then:
        expected==actual
    }

    def "when toString custom string created"(){
        setup:
        Credential cred = new Credential(id: "foo", scope: "openshift", env: "prod", prefix: "bar")
        String expected = "{\"type\":\"usernamePassword\", \"id\":\"foo\", \"prefix\":\"bar\", \"env\":\"prod\", \"scope\":\"openshift\"}"
        when:
        String actual = cred.toString()
        then:
        expected==actual
    }

}
