package com.macindex.macindex;

/**
 * Resolves stable machine identities during user record upgrades.
 */
interface MachineIdentityResolver {

    String resolveUID(String machineUID);

    String resolveLegacyName(String machineName);

    String getIdentityName(String machineUID);
}
