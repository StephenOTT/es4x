/*
 * Copyright 2018 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.reactiverse.es4x.nashorn;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.spi.VerticleFactory;
import jdk.nashorn.api.scripting.JSObject;

public class NashornVerticleFactory implements VerticleFactory {

  private Vertx vertx;

  @Override
  public void init(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public String prefix() {
    return "js";
  }

  @Override
  public Verticle createVerticle(String verticleName, ClassLoader classLoader) throws Exception {

    final Loader loader;

    synchronized (this) {
      // create a new CommonJS loader
      loader = new Loader(vertx);
    }

    return new Verticle() {

      private Vertx vertx;
      private Context context;

      private Object self;
      private JSObject stop;

      @Override
      public Vertx getVertx() {
        return vertx;
      }

      @Override
      public void init(Vertx vertx, Context context) {
        this.vertx = vertx;
        this.context = context;
      }

      @Override
      public void start(Future<Void> startFuture) throws Exception {
        // expose config
        if (context != null && context.config() != null) {
          loader.config(context.config());
        }

        final String fsVerticleName;

        // extract prefix if present
        if (verticleName.startsWith(prefix() + ":")) {
          fsVerticleName = verticleName.substring(prefix().length() + 1);
        } else {
          fsVerticleName = verticleName;
        }

        // nashorn can take some time to load so it might block the event loop
        // this is usually not a issue as it is a one time operation
        self = loader.main(fsVerticleName);

        // if the main module exports 2 function we bind those to the verticle lifecycle
        if (self instanceof JSObject) {
          Object start = ((JSObject) self).getMember("start");
          if (start instanceof JSObject) {
            ((JSObject) start).call(self);
          }

          Object stop = ((JSObject) self).getMember("stop");
          if (stop instanceof JSObject) {
            this.stop = (JSObject) stop;
          }
        }
        startFuture.complete();
      }

      @Override
      public void stop(Future<Void> stopFuture) throws Exception {
        if (stop != null) {
          try {
            stop.call(self);
          } catch (RuntimeException e) {
            stopFuture.fail(e);
          }
        }
        // done!
        stopFuture.complete();
      }
    };
  }
}
