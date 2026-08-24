package testutil;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;

import java.io.File;
import java.net.URISyntaxException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class GitHelpers {
    public static void withRemoteGit(File remote, File workingDir, BiConsumer<Git, Git> work) {
        try (Git remoteGit = Git.init().setBare(false).setDirectory(remote).setInitialBranch("main").call()) {
            remoteGit.commit().setMessage("initial").setSign(false).call();
            try (Git cloneGit = Git.cloneRepository().setCloneAllBranches(true)
                    .setURI(remote.toURI().toString())
                    .setDirectory(workingDir)
                    .setBare(false)
                    .setBranch("main")
                    .call()) {
                cloneGit.remoteAdd().setName("origin").setUri(new URIish().setRawPath(remote.toURI().toString()));
                work.accept(remoteGit, cloneGit);
            }
        } catch (GitAPIException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
