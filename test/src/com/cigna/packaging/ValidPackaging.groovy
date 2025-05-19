package com.cigna.packaging

import com.cigna.packaging.Packaging

public class ValidPackaging extends Packaging  {

    @Override
    String getPodConfig() {
        return '{}'
    }

    @Override
    void packageApplication() {
        script.echo('test echo')
    }

}
