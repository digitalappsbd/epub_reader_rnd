/*
 * Module: r2-testapp-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. European Digital Reading Lab. All rights reserved.
 * Licensed to the Readium Foundation under one or more contributor license agreements.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package com.digitalappsbd.app.epurreader.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.digitalappsbd.app.epurreader.CatalogActivity;
import org.readium.r2.shared.Publication;

/**
 * Created by Aferdita Muriqi on 1/16/18.
 */

public class R2IntentHelper {

  public static final String URI = "URI";
  public static final String EXTENSION = "EXTENSION";


  public Intent catalogActivityIntent(Context context, Uri uri, Publication.EXTENSION extension) {
    Intent i = new Intent(context, CatalogActivity.class);
    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    i.putExtra(EXTENSION, extension.getValue());
    i.putExtra(URI, uri.toString());
    return i;
  }
}