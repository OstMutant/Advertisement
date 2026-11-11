package org.ost.platform.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/** Unit tests for {@link ComponentFactory}: what each accessor does when the component is installed, absent or ambiguous. */
class ComponentFactoryTest {

    /** The component type the factory under test stands for. */
    interface Sample {
    }

    private static ComponentFactory<Sample> factoryWith(Sample... installed) {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        for (int i = 0; i < installed.length; i++) {
            beans.registerSingleton("sample" + i, installed[i]);
        }
        return new ComponentFactory<>(beans.getBeanProvider(Sample.class));
    }

    @Test
    void get_returnsTheInstalledComponent() {
        Sample installed = new Sample() { };

        assertThat(factoryWith(installed).get()).isSameAs(installed);
    }

    @Test
    void get_withNothingInstalled_throwsTheTypedExceptionNamingTheType() {
        assertThatExceptionOfType(OptionalComponentUnavailableException.class)
                .isThrownBy(factoryWith()::get)
                .satisfies(e -> assertThat(e.getComponentType()).isEqualTo(Sample.class))
                .withMessageContaining("Sample");
    }

    @Test
    void get_withSeveralInstalled_keepsSpringsOwnExceptionBecauseThatIsAWiringFault() {
        assertThatExceptionOfType(NoUniqueBeanDefinitionException.class)
                .isThrownBy(factoryWith(new Sample() { }, new Sample() { })::get);
    }

    @Test
    void absentSafeAccessors_withNothingInstalled_answerEmptyAndNeverThrow() {
        ComponentFactory<Sample> factory = factoryWith();
        AtomicBoolean consumed = new AtomicBoolean();

        factory.ifAvailable(component -> consumed.set(true));

        assertThat(factory.getIfAvailable()).isNull();
        assertThat(factory.findIfAvailable()).isEmpty();
        assertThat(consumed).isFalse();
    }

    @Test
    void absentSafeAccessors_withAComponentInstalled_returnIt() {
        Sample installed = new Sample() { };
        ComponentFactory<Sample> factory = factoryWith(installed);
        AtomicBoolean consumed = new AtomicBoolean();

        factory.ifAvailable(component -> consumed.set(component == installed));

        assertThat(factory.getIfAvailable()).isSameAs(installed);
        assertThat(factory.findIfAvailable()).containsSame(installed);
        assertThat(consumed).isTrue();
    }
}
