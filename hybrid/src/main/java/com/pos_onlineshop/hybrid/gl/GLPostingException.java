package com.pos_onlineshop.hybrid.gl;

/** Base type for GL posting failures. Unchecked so a failed post rolls back its enclosing transaction. */
public class GLPostingException extends RuntimeException {
    public GLPostingException(String message) {
        super(message);
    }
}
