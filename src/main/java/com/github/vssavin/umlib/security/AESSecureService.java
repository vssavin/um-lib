package com.github.vssavin.umlib.security;

import io.github.vssavin.securelib.AESSecure;
import org.springframework.stereotype.Service;

/**
 * Provides a service that uses the AES encryption algorithm.
 *
 * @author vssavin on 18.01.22
 */
@Service
class AESSecureService extends AESSecure implements SecureService {
    @Override
    public String toString() {
        return "AES";
    }
}
