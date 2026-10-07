package com.elitesavior.vasthall.engine;

/**
 * Opaque bind id. Unreal mental model: {@code FDelegateHandle}.
 *
 * <p>A handle is valid while the {@link MulticastDelegate} still owns that
 * listener. {@link MulticastDelegate#unbind(DelegateHandle)} invalidates it.
 */
public final class DelegateHandle {
    private long id;

    public DelegateHandle() {
        this(0L);
    }

    DelegateHandle(long id) {
        this.id = id;
    }

    public boolean isValid() {
        return id != 0L;
    }

    public long id() {
        return id;
    }

    void invalidate() {
        id = 0L;
    }
}
