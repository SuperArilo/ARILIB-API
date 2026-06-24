package com.tty.api.service.placeholder;

import com.tty.api.enumType.PlaceholderTypeEnum;

public interface PlaceholderDefinition<E> {

    E key();
    PlaceholderResolve resolver();

    static <E extends Enum<E> & PlaceholderTypeEnum> PlaceholderDefinition<E> of(E key, PlaceholderResolve resolver) {
        return new PlaceholderDefinition<>() {
            @Override
            public E key() {
                return key;
            }

            @Override
            public PlaceholderResolve resolver() {
                return resolver;
            }
        };
    }

}

