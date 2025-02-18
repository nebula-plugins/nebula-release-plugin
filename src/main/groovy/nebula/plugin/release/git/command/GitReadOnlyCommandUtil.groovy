package nebula.plugin.release.git.command

import org.gradle.api.GradleException
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GitReadOnlyCommandUtil implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(GitReadOnlyCommandUtil)
    private final ProviderFactory providers

    private Provider usernameFromLogProvider
    private Provider emailFromLogProvider
    private File rootDir

    GitReadOnlyCommandUtil(ProviderFactory providerFactory) {
        this.providers = providerFactory
    }

    /**
     * Register the providers for the read only commands
     * This uses the provider factory to create the providers in order to support ocnfiguration cache
     * @param gitRoot
     */
    void configure(File gitRoot, boolean shouldVerifyUserGitConfig) {
        this.rootDir = gitRoot
        usernameFromLogProvider = providers.of(UsernameFromLog.class) {
            it.parameters.rootDir.set(rootDir)
        }
        emailFromLogProvider = providers.of(EmailFromLog.class) {
            it.parameters.rootDir.set(rootDir)
        }
        if(shouldVerifyUserGitConfig) {
            verifyUserGitConfig()
        }
    }

    private void verifyUserGitConfig() {
        String username = getGitConfig('user.name')
        String email = getGitConfig('user.email')
        if(username && email) {
            return
        }
        String globalUsername = getGitConfig('--global', 'user.name')
        String globalEmail = getGitConfig('--global', 'user.email')
        if(globalUsername && globalEmail) {
            return
        }
        String systemUsername = getGitConfig('--system', 'user.name')
        String systemEmail = getGitConfig('--system', 'user.email')
        if(systemUsername && systemEmail) {
            return
        }
        String localUsername = getGitConfig('--local', 'user.name')
        String localEmail = getGitConfig('--local', 'user.email')
        if(localUsername && localEmail) {
            return
        }
        String usernameFromLog = usernameFromLogProvider.isPresent() ? usernameFromLogProvider.get() : null
        if(!username && !globalUsername && !localUsername && !systemUsername && usernameFromLog) {
            throw new GradleException("Git user.name is not set. Please configure git user.name globally, locally or system wide. You can learn more in https://git-scm.com/book/en/v2/Getting-Started-First-Time-Git-Setup")
        }
        String emailFromLog =  emailFromLogProvider.isPresent() ? emailFromLogProvider.get() : null
        if(!email && !globalEmail && !localEmail && !systemEmail && emailFromLog) {
            throw new GradleException("Git user.email is not set. Please configure git user.email globally, locally or system wide. You can learn more in https://git-scm.com/book/en/v2/Getting-Started-First-Time-Git-Setup")
        }
    }


    /**
     * Returns a git config value for a given scope
     * @param scope
     * @param configKey
     * @return
     */
    String getGitConfig(String scope, String configKey) {
        try {
            def getConfigValueProvider = providers.of(GetGitConfigValue.class) {
                it.parameters.rootDir.set(rootDir)
                it.parameters.gitConfigScope.set(scope)
                it.parameters.gitConfigKey.set(configKey)
            }
            return getConfigValueProvider.get().toString()?.
                    replaceAll("\n", "")?.toString()
        } catch(Exception e) {
            logger.debug("Could not get git config {} {} {}", scope, configKey)
            return null
        }
    }

    /**
     * Returns a git config value for a given scope
     * @param configKey
     * @return
     */
    String getGitConfig(String configKey) {
        try {
            def getConfigValueProvider = providers.of(GetGitConfigValue.class) {
                it.parameters.rootDir.set(rootDir)
                it.parameters.gitConfigKey.set(configKey)
            }
            return getConfigValueProvider.get().toString()?.
                    replaceAll("\n", "")?.toString()
        } catch(Exception e) {
            logger.debug("Could not get git config {} {}", configKey)
            return null
        }
    }
}
