package com.nerdwin15.stash.webhook.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.junit.Before;
import org.junit.Test;

import com.atlassian.stash.i18n.I18nService;
import com.atlassian.stash.nav.NavBuilder;
import com.atlassian.stash.nav.NavBuilder.Repo;
import com.atlassian.stash.nav.NavBuilder.RepoClone;
import com.atlassian.stash.project.Project;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.ssh.api.SshCloneUrlResolver;
import com.atlassian.stash.ssh.api.SshConfiguration;
import com.atlassian.stash.ssh.api.SshConfigurationService;
import com.atlassian.stash.user.PermissionValidationService;
import com.nerdwin15.stash.webhook.Notifier;
import com.sun.jersey.api.client.ClientResponse.Status;

/**
 * Test case for the JenkinsResource class.
 * 
 * @author Michael Irwin (mikesir87)
 */
public class JenkinsResourceTest {

  private static final String JENKINS_BASE = "http://jenkins.localhost/jenkins";
  private static final boolean IGNORE_CERTS = false;
  private static final boolean OMIT_HASH_CODE = false;

  private static final String HTTP_URL = 
      "https://stash.localhost/stash/scm/test/test.git";
  private static final String SSH_URL = 
      "ssh://git@stash.localhost:7999/test/test.git";
  private static final String EMPTY_SSH_URL = "";
  
  private JenkinsResource resource;
  private Notifier notifier; 
  private PermissionValidationService permissionValidationService; 
  private I18nService i18nService;
  private NavBuilder navBuilder;
  private SshConfigurationService sshConfigurationService;
  private SshCloneUrlResolver sshCloneUrlResolver;
  
  private Repository repository;
  
  /**
   * Setup tasks
   */
  @Before
  public void setup() throws Exception {
    notifier = mock(Notifier.class);
    permissionValidationService = mock(PermissionValidationService.class);
    i18nService = mock(I18nService.class);
    navBuilder = mock(NavBuilder.class);
    sshConfigurationService = mock(SshConfigurationService.class);
    
    sshCloneUrlResolver = mock(SshCloneUrlResolver.class);
    
    resource = new JenkinsResource(notifier, permissionValidationService, 
        i18nService, navBuilder, sshConfigurationService, sshCloneUrlResolver);
    
    repository = mock(Repository.class);
    Project project = mock(Project.class);
    when(repository.getProject()).thenReturn(project);
    when(project.getKey()).thenReturn("KEY");
    when(repository.getSlug()).thenReturn("SLUG");
  }

  /**
   * Validate that if a null JenkinsBase is provided, a BAD_REQUEST is returned.
   */
  @Test
  public void shouldFailWhenJenkinsBaseNullProvidedToTest() {
    Map<String, Object> result = 
        resource.test(repository, null, "http", null, IGNORE_CERTS, OMIT_HASH_CODE);
    assertFalse((Boolean) result.get("successful"));
  }

  /**
   * Validate that if a null CloneType is provided, a BAD_REQUEST is returned.
   */
  @Test
  public void shouldFailWhenCloneTypeNullProvidedToTest() {
    Map<String, Object> result =
        resource.test(repository, JENKINS_BASE, null, HTTP_URL, IGNORE_CERTS, OMIT_HASH_CODE);
    assertFalse((Boolean) result.get("successful"));
  }

  /**
   * Validate that if a null repo clone url is provided when the clone type 
   * is set to CUSTOM, then a BAD_REQUEST is returned.
   */
  @Test
  public void shouldFailWhenCloneUrlNullProvidedToTest() {
    Map<String, Object> result = 
        resource.test(repository, JENKINS_BASE, "custom", null, IGNORE_CERTS, OMIT_HASH_CODE);
    assertFalse((Boolean) result.get("successful"));
  }
  
  /**
   * Validate that the config endpoint creates the expected results.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void testConfigResource() {
    Repo repo = mock(Repo.class);
    RepoClone repoClone = mock(RepoClone.class);
    
    when(navBuilder.repo(repository)).thenReturn(repo);
    when(repo.clone("git")).thenReturn(repoClone);
    when(repoClone.buildAbsoluteWithoutUsername()).thenReturn(HTTP_URL);
    
    SshConfiguration sshConfiguration = mock(SshConfiguration.class);
    
    when(sshConfigurationService.getConfiguration()).thenReturn(sshConfiguration);
    when(sshConfiguration.isEnabled()).thenReturn(true);
    when(sshCloneUrlResolver.getCloneUrl(repository)).thenReturn(SSH_URL);
    
    Response response = resource.config(repository);
    assertEquals(Status.OK.getStatusCode(), response.getStatus());
    Map<String, String> data = (Map<String, String>) response.getEntity();
    assertEquals(data.get("ssh"), SSH_URL);
    assertEquals(data.get("http"), HTTP_URL);
    
    verify(sshCloneUrlResolver).getCloneUrl(repository);
    verify(repoClone).buildAbsoluteWithoutUsername();
  }
  
  /**
   * Validate that the config endpoint safely handles the case when the internal SSH
   * server is disabled in Stash 3.0 and later.
   */
  @Test
  public void shouldNotProduceExceptionWhenSshDisabled() {
    Repo repo = mock(Repo.class);
    RepoClone repoClone = mock(RepoClone.class);
    
    when(navBuilder.repo(repository)).thenReturn(repo);
    when(repo.clone("git")).thenReturn(repoClone);
    when(repoClone.buildAbsoluteWithoutUsername()).thenReturn(HTTP_URL);

    SshConfiguration sshConfiguration = mock(SshConfiguration.class);

    when(sshConfigurationService.getConfiguration()).thenReturn(sshConfiguration);
    when(sshConfiguration.isEnabled()).thenReturn(false);
    when(sshCloneUrlResolver.getCloneUrl(repository)).thenThrow(
            new IllegalStateException("Internal SSH server is disabled"));
    
    Response response = resource.config(repository);
    assertEquals(Status.OK.getStatusCode(), response.getStatus());
    Map<String, String> data = (Map<String, String>) response.getEntity();
    assertEquals(data.get("ssh"), EMPTY_SSH_URL);
    assertEquals(data.get("http"), HTTP_URL);
    
    verify(sshCloneUrlResolver, never()).getCloneUrl(repository);
    verify(repoClone).buildAbsoluteWithoutUsername();
  }
  
}
