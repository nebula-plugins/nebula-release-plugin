package nebula.plugin.release.util

import groovy.transform.CompileDynamic
import org.codehaus.groovy.runtime.GeneratedClosure
import org.gradle.internal.metaobject.ConfigureDelegate
import org.gradle.util.Configurable
import org.gradle.util.internal.ClosureBackedAction

import javax.annotation.Nullable

class ConfigureUtil {
    public static final int DELEGATE_FIRST = 1
    public static final int OWNER_ONLY = 2

    /**
     * <p>Configures {@code target} with {@code configureClosure}, via the {@link Configurable} interface if necessary.</p>
     *
     * <p>If {@code target} does not implement {@link Configurable} interface, it is set as the delegate of a clone of
     * {@code configureClosure} with a resolve strategy of {@code DELEGATE_FIRST}.</p>
     *
     * <p>If {@code target} does implement the {@link Configurable} interface, the {@code configureClosure} will be passed to
     * {@code delegate}'s {@link Configurable#configure(Closure)} method.</p>
     *
     * @param configureClosure The configuration closure
     * @param target The object to be configured
     * @return The delegate param
     */
    static <T> T configure(@Nullable Closure configureClosure, T target) {
        if (configureClosure == null) {
            return target
        }

        if (target instanceof Configurable) {
            ((Configurable) target).configure(configureClosure)
        } else {
            configureTarget(configureClosure, target, new ConfigureDelegate(configureClosure, target))
        }

        return target
    }

    @CompileDynamic
    private static <T> void configureTarget(Closure configureClosure, T target, ConfigureDelegate closureDelegate) {
        if (!(configureClosure instanceof GeneratedClosure)) {
            new ClosureBackedAction<T>(configureClosure, DELEGATE_FIRST, false).execute(target)
            return;
        }

        // Hackery to make closure execution faster, by short-circuiting the expensive property and method lookup on Closure
        Closure withNewOwner = configureClosure.rehydrate(target, closureDelegate, configureClosure.getThisObject())
        new ClosureBackedAction<T>(withNewOwner, OWNER_ONLY, false).execute(target)
    }
}
