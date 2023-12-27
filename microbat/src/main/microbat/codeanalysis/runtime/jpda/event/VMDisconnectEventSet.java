/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */

package microbat.codeanalysis.runtime.jpda.event;

import com.sun.jdi.event.EventSet;

public class VMDisconnectEventSet extends AbstractEventSet {

  private static final long serialVersionUID = 7968123152344675342L;

  VMDisconnectEventSet(EventSet jdiEventSet) {
    super(jdiEventSet);
  }

  @Override
  public void notify(JDIListener listener) {
    listener.vmDisconnect(this);
  }
}
