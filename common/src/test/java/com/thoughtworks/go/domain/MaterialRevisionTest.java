/*
 * Copyright Thoughtworks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thoughtworks.go.domain;

import com.thoughtworks.go.config.CaseInsensitiveString;
import com.thoughtworks.go.config.materials.Filter;
import com.thoughtworks.go.config.materials.IgnoredFiles;
import com.thoughtworks.go.config.materials.ScmMaterialConfig;
import com.thoughtworks.go.config.materials.SubprocessExecutionContext;
import com.thoughtworks.go.config.materials.dependency.DependencyMaterial;
import com.thoughtworks.go.config.materials.mercurial.HgMaterial;
import com.thoughtworks.go.config.materials.svn.SvnMaterial;
import com.thoughtworks.go.domain.materials.*;
import com.thoughtworks.go.domain.materials.dependency.DependencyMaterialRevision;
import com.thoughtworks.go.domain.materials.mercurial.StringRevision;
import com.thoughtworks.go.helper.GoConfigMother;
import com.thoughtworks.go.helper.HgTestRepo;
import com.thoughtworks.go.helper.MaterialsMother;
import com.thoughtworks.go.helper.ModificationsMother;
import com.thoughtworks.go.util.TempDirUtils;
import com.thoughtworks.go.util.command.InMemoryStreamConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.thoughtworks.go.helper.ModificationsMother.*;
import static com.thoughtworks.go.util.command.ProcessOutputStreamConsumer.inMemoryConsumer;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class MaterialRevisionTest {
    private static final StringRevision REVISION_0 = new StringRevision("b61d12de515d82d3a377ae3aae6e8abe516a2651");
    private static final StringRevision REVISION_2 = new StringRevision("ca3ebb67f527c0ad7ed26b789056823d8b9af23f");
    @TempDir
    Path tempDir;

    private HgMaterial hgMaterial;
    private File workingFolder;

    @BeforeEach
    public void setUp() throws Exception {
        HgTestRepo hgTestRepo = new HgTestRepo("hgTestRepo1", tempDir);
        hgMaterial = MaterialsMother.hgMaterial(hgTestRepo.projectRepositoryUrl());
        workingFolder = TempDirUtils.createRandomDirectoryIn(tempDir).toFile();
    }

    @Test
    public void shouldGetModifiedTimeFromTheLatestModification() {
        final MaterialRevision materialRevision = new MaterialRevision(MaterialsMother.hgMaterial(), multipleModificationsInHg());
        assertThat(materialRevision.getDateOfLatestModification()).isEqualTo(ModificationsMother.TODAY_CHECKIN);
    }

    @Test
    public void shouldDetectChangesAfterACheckin() throws Exception {
        MaterialRevision original = new MaterialRevision(hgMaterial,
                hgMaterial.modificationsSince(workingFolder, REVISION_0, new TestSubprocessExecutionContext()));

        checkInOneFile(hgMaterial);
        checkInOneFile(hgMaterial);
        checkInOneFile(hgMaterial);

        final MaterialRevision after = findNewRevision(original, hgMaterial, workingFolder, new TestSubprocessExecutionContext());

        assertThat(after).isNotEqualTo(original);
        assertThat(after.numberOfModifications()).isEqualTo(3);
        assertThat(after.getRevision()).isNotEqualTo(original.getRevision());
        assertThat(after.hasChangedSince(original)).isTrue();

    }

    @Test
    public void shouldMarkRevisionAsChanged() throws Exception {
        MaterialRevision original = new MaterialRevision(hgMaterial, hgMaterial.modificationsSince(workingFolder, REVISION_0, new TestSubprocessExecutionContext()));

        checkInFiles(hgMaterial, "user.doc");

        MaterialRevision newRev = findNewRevision(original, hgMaterial, workingFolder, new TestSubprocessExecutionContext());
        assertThat(newRev.isChanged()).isTrue();
    }

    @Test
    public void shouldMarkRevisionAsNotChanged() throws Exception {
        List<Modification> modifications = hgMaterial.latestModification(workingFolder, new TestSubprocessExecutionContext());
        MaterialRevision original = new MaterialRevision(hgMaterial, modifications);
        checkInFiles(hgMaterial, "user.doc");
        original = findNewRevision(original, hgMaterial, workingFolder, new TestSubprocessExecutionContext());

        MaterialRevision newRev = findNewRevision(original, hgMaterial, workingFolder, new TestSubprocessExecutionContext());
        assertThat(newRev.isChanged()).isFalse();
    }

    @Test
    public void shouldIgnoreDocumentCheckin() throws Exception {
        MaterialRevision previousRevision = new MaterialRevision(hgMaterial, hgMaterial.modificationsSince(workingFolder, REVISION_0, new TestSubprocessExecutionContext()));

        Filter filter = new Filter(new IgnoredFiles("**/*.doc"));
        hgMaterial.setFilter(filter);

        checkInFiles(hgMaterial, "user.doc");

        MaterialRevision newRevision = findNewRevision(previousRevision, hgMaterial, workingFolder, new TestSubprocessExecutionContext());
        assertThat(newRevision.filter(previousRevision)).isEqualTo(previousRevision);
    }

    @Test
    public void shouldIgnoreDocumentWhenCheckin() throws Exception {
        MaterialRevision original = new MaterialRevision(hgMaterial, hgMaterial.modificationsSince(workingFolder, REVISION_0, new TestSubprocessExecutionContext()));

        Filter filter = new Filter(new IgnoredFiles("helper/**/*.*"));
        hgMaterial.setFilter(filter);

        checkInFiles(hgMaterial,
                "helper/topics/installing_go_agent.xml",
                "helper/topics/installing_go_server.xml");

        MaterialRevision newRev = findNewRevision(original, hgMaterial, workingFolder, new TestSubprocessExecutionContext());
        assertThat(newRev.filter(original)).isEqualTo(original);
    }

    @Test
    public void shouldIgnoreDocumentsWithSemanticallyEqualsIgnoreFilter() throws Exception {
        MaterialRevision original = new MaterialRevision(hgMaterial, hgMaterial.modificationsSince(workingFolder, REVISION_0, new TestSubprocessExecutionContext()));

        Filter filter = new Filter(new IgnoredFiles("**/*.doc"), new IgnoredFiles("*.doc"));
        hgMaterial.setFilter(filter);

        checkInFiles(hgMaterial, "user.doc");

        MaterialRevision newRev = findNewRevision(original, hgMaterial, workingFolder, new TestSubprocessExecutionContext());
        assertThat(newRev.filter(original)).isEqualTo(original);
    }

    @Test
    public void shouldIncludeJavaFileWithSemanticallyEqualsIgnoreFilter() throws Exception {
        MaterialRevision original = new MaterialRevision(hgMaterial,
                hgMaterial.modificationsSince(workingFolder, REVISION_0, new TestSubprocessExecutionContext()));

        Filter filter = new Filter(new IgnoredFiles("**/*.doc"), new IgnoredFiles("*.doc"));
        GoConfigMother.createPipelineConfig(filter, (ScmMaterialConfig) hgMaterial.config());
        checkInFiles(hgMaterial, "A.java");
        checkInFiles(hgMaterial, "B.doc");
        checkInFiles(hgMaterial, "C.pdf");

        MaterialRevision newRev = findNewRevision(original, hgMaterial, workingFolder, new TestSubprocessExecutionContext());
        assertThat(newRev.filter(original)).isEqualTo(newRev);
    }

    @Test
    public void shouldNotIgnoreJavaFile() throws Exception {
        MaterialRevision original = new MaterialRevision(hgMaterial,
                hgMaterial.modificationsSince(workingFolder, REVISION_0, new TestSubprocessExecutionContext()));

        Filter filter = new Filter(new IgnoredFiles("**/*.doc"));
        GoConfigMother.createPipelineConfig(filter, (ScmMaterialConfig) hgMaterial.config());
        checkInFiles(hgMaterial, "A.java");

        MaterialRevision newRev = findNewRevision(original, hgMaterial, workingFolder, new TestSubprocessExecutionContext());
        assertThat(newRev.filter(original)).isEqualTo(newRev);
    }

    @Test
    public void shouldNotIgnoreAnyFileIfFilterIsNotDefinedForTheGivenMaterial() throws Exception {
        MaterialRevision original = new MaterialRevision(hgMaterial,
                hgMaterial.modificationsSince(workingFolder, REVISION_0, new TestSubprocessExecutionContext()));

        Filter filter = new Filter();
        GoConfigMother.createPipelineConfig(filter, (ScmMaterialConfig) hgMaterial.config());
        checkInFiles(hgMaterial, "A.java");

        MaterialRevision newRev = findNewRevision(original, hgMaterial, workingFolder, new TestSubprocessExecutionContext());
        assertThat(newRev.filter(original)).isEqualTo(newRev);
    }

    @Test
    public void shouldMarkRevisionChangeFalseIfNoNewChangesAvailable() {
        Modification modificationForRevisionTip = new Modification(new Date(), REVISION_2.getRevision(), "MOCK_LABEL-12", null);
        MaterialRevision revision = new MaterialRevision(hgMaterial, modificationForRevisionTip);
        MaterialRevision unchangedRevision = findNewRevision(revision, hgMaterial, workingFolder, new TestSubprocessExecutionContext());

        assertThat(unchangedRevision.isChanged()).isFalse();
    }

    @Test
    public void shouldReturnOnlyLatestModificationIfNoNewChangesAvailable() {
        Modification modificationForRevisionTip = new Modification("Unknown", "Unknown", null, new Date(), REVISION_2.getRevision());
        Modification olderModification = new Modification("Unknown", "Unknown", null, new Date(), REVISION_0.getRevision());
        MaterialRevision revision = new MaterialRevision(hgMaterial, modificationForRevisionTip, olderModification);
        MaterialRevision unchangedRevision = findNewRevision(revision, hgMaterial, workingFolder, new TestSubprocessExecutionContext());

        assertThat(unchangedRevision.getModifications().size()).isEqualTo(1);
        assertThat(unchangedRevision.getModifications().get(0)).isEqualTo(modificationForRevisionTip);
    }

    @Test
    public void shouldNotConsiderChangedFlagAsPartOfEqualityAndHashCodeCheck() {
        Modification modification = oneModifiedFile("revision1");
        SvnMaterial material = MaterialsMother.svnMaterial();

        MaterialRevision notChanged = new MaterialRevision(material, false, modification);
        MaterialRevision changed = new MaterialRevision(material, true, modification);
        changed.markAsChanged();

        assertThat(changed).isEqualTo(notChanged);
        assertThat(changed.hashCode()).isEqualTo(notChanged.hashCode());
    }

    @Test
    public void shouldDetectChangedRevision() {
        Modification modification1 = oneModifiedFile("revision1");
        Modification modification2 = oneModifiedFile("revision2");
        SvnMaterial material = MaterialsMother.svnMaterial();
        MaterialRevision materialRevision1 = new MaterialRevision(material, modification1);
        MaterialRevision materialRevision2 = new MaterialRevision(material, modification2);
        assertThat(materialRevision1.hasChangedSince(materialRevision2)).isTrue();
    }

    @Test
    public void shouldDisplayRevisionAsBuildCausedByForDependencyMaterial() {
        DependencyMaterial dependencyMaterial = new DependencyMaterial(new CaseInsensitiveString("upstream"), new CaseInsensitiveString("stage"));
        MaterialRevision materialRevision = new MaterialRevision(dependencyMaterial, new Modification(new Date(), "upstream/2/stage/1", "1.3-2", null));
        assertThat(materialRevision.buildCausedBy()).isEqualTo("upstream/2/stage/1");
    }

    @Test
    public void shouldUseLatestMaterial() {
        MaterialRevision original = new MaterialRevision(hgMaterial,
                hgMaterial.modificationsSince(workingFolder, REVISION_0, new TestSubprocessExecutionContext()));

        HgMaterial newMaterial = MaterialsMother.hgMaterial(hgMaterial.getUrl());
        newMaterial.setFilter(new Filter(new IgnoredFiles("**/*.txt")));
        final MaterialRevision after = findNewRevision(original, newMaterial, workingFolder, new TestSubprocessExecutionContext());

        assertThat(after.getMaterial()).isEqualTo(newMaterial);
    }

    @Test
    public void shouldDetectLatestAndOldestModification() {
        MaterialRevision materialRevision = new MaterialRevision(hgMaterial, modification("3"), modification("2"), modification("1"));

        assertThat(materialRevision.getLatestModification()).isEqualTo(modification("3"));
        assertThat(materialRevision.getOldestModification()).isEqualTo(modification("1"));
    }

    @Test
    public void shouldDetectLatestRevision() {
        MaterialRevision materialRevision = new MaterialRevision(hgMaterial, modification("3"), modification("2"), modification("1"));
        assertThat(materialRevision.getRevision()).isEqualTo(new StringRevision("3"));
    }

    @Test
    public void shouldDetectOldestScmRevision() {
        MaterialRevision materialRevision = new MaterialRevision(hgMaterial, modification("3"), modification("2"), modification("1"));
        assertThat(materialRevision.getOldestRevision()).isEqualTo(new StringRevision("1"));
    }

    @Test
    public void shouldDetectOldestAndLatestDependencyRevision() {
        DependencyMaterial dependencyMaterial = new DependencyMaterial(new CaseInsensitiveString("upstream"), new CaseInsensitiveString("stage"));
        MaterialRevision materialRevision = new MaterialRevision(dependencyMaterial, new Modification(new Date(), "upstream/3/stage/1", "1.3-3", null),
                new Modification(new Date(), "upstream/2/stage/1", "1.3-2", null));
        assertThat(materialRevision.getOldestRevision()).isEqualTo(DependencyMaterialRevision.create("upstream/2/stage/1", "1.3-2"));
        assertThat(materialRevision.getRevision()).isEqualTo(DependencyMaterialRevision.create("upstream/3/stage/1", "1.3-3"));
    }

    @Test
    public void shouldReturnNullRevisionWhenThereIsNoMaterial() {
        Revision revision = new MaterialRevision(null).getRevision();
        assertThat(revision).isNotNull();
        assertThat(revision.getRevision()).isEqualTo("");
    }

    @Test
    public void shouldReturnFullRevisionForTheLatestModification() {
        assertThat(hgRevision().getLatestRevisionString()).isEqualTo("012345678901234567890123456789");
    }

    private MaterialRevision hgRevision() {
        return new MaterialRevision(hgMaterial, modification("012345678901234567890123456789"), modification("2"), modification("1"));
    }

    @Test
    public void shouldReturnShortRevisionForTheLatestModification() {
        assertThat(hgRevision().getLatestShortRevision()).isEqualTo("012345678901");
    }

    @Test
    public void shouldReturnMaterialName() {
        assertThat(hgRevision().getMaterialName()).isEqualTo((hgMaterial.getDisplayName()));
    }

    @Test
    public void shouldReturnTruncatedMaterialName() {
        assertThat(hgRevision().getTruncatedMaterialName()).isEqualTo((hgMaterial.getTruncatedDisplayName()));
    }

    @Test
    public void shouldReturnMaterialType() {
        assertThat(hgRevision().getMaterialType()).isEqualTo("Mercurial");
    }

    @Test
    public void shouldReturnLatestComments() {
        assertThat(hgRevision().getLatestComment()).isEqualTo("Checkin 012345678901234567890123456789");
    }

    @Test
    public void shouldReturnLatestUser() {
        assertThat(hgRevision().getLatestUser()).isEqualTo("user");
    }

    @Test
    public void shouldRemoveFromThisWhateverModificationIsPresentInThePassedInRevision() {
        MaterialRevision revision = createHgMaterialWithMultipleRevisions(1, oneModifiedFile("rev2"), oneModifiedFile("rev1")).getMaterialRevision(0);
        MaterialRevision passedIn = createHgMaterialWithMultipleRevisions(1, oneModifiedFile("rev1")).getMaterialRevision(0);

        MaterialRevision expected = createHgMaterialWithMultipleRevisions(1, oneModifiedFile("rev2")).getMaterialRevision(0);

        assertThat(revision.subtract(passedIn)).isEqualTo(expected);
    }

    @Test
    public void shouldReturnCurrentIfThePassedInDoesNotHaveAnythingThatCurrentHas() {
        MaterialRevision revision = createHgMaterialWithMultipleRevisions(1, oneModifiedFile("rev2")).getMaterialRevision(0);
        MaterialRevision passedIn = createHgMaterialWithMultipleRevisions(1, oneModifiedFile("rev1")).getMaterialRevision(0);

        MaterialRevision expected = createHgMaterialWithMultipleRevisions(1, oneModifiedFile("rev2")).getMaterialRevision(0);
        assertThat(revision.subtract(passedIn)).isEqualTo(expected);
    }

    private Modification modification(String revision) {
        return new Modification("user", "Checkin "
                + revision, null, null, revision);
    }

    private void checkInOneFile(HgMaterial hgMaterial) throws Exception {
        checkInFiles(hgMaterial, UUID.randomUUID().toString());
    }

    private void checkInFiles(HgMaterial hgMaterial, String... fileNames) throws Exception {
        final File localDir = TempDirUtils.createRandomDirectoryIn(tempDir).toFile();
        InMemoryStreamConsumer consumer = inMemoryConsumer();
        Revision revision = latestRevision(hgMaterial, workingFolder, new TestSubprocessExecutionContext());
        hgMaterial.updateTo(consumer, localDir, new RevisionContext(revision), new TestSubprocessExecutionContext());
        for (String fileName : fileNames) {
            File file = new File(localDir, fileName);
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), "", UTF_8);
            hgMaterial.add(localDir, consumer, file);
        }
        hgMaterial.commit(localDir, consumer, "Adding a new file.", "TEST");
        hgMaterial.push(localDir, consumer);
    }

    private Revision latestRevision(HgMaterial material, File workingDir, TestSubprocessExecutionContext execCtx) {
        List<Modification> modifications = material.latestModification(workingDir, execCtx);
        return new Modifications(modifications).latestRevision(material);
    }

    public MaterialRevision findNewRevision(MaterialRevision materialRevision, HgMaterial material, File workingFolder, final SubprocessExecutionContext execCtx) {
        List<Modification> newModifications = material.modificationsSince(workingFolder, materialRevision.getRevision(), execCtx);
        return materialRevision.latestChanges(material, materialRevision.getModifications(), newModifications);
    }
}
