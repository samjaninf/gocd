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
import classNames from "classnames/bind";
import {MithrilViewComponent} from "jsx/mithril-component";
import m from "mithril";
import Stream from "mithril/stream";
import {MaterialRevision, Modification, PipelineRunInfo} from "models/pipeline_activity/pipeline_activity";
import {Dropdown, DropdownAttrs} from "views/components/buttons";
import * as Icons from "views/components/icons";
import styles from "./index.scss";

const classnames = classNames.bind(styles);

interface Attrs {
  pipelineRunInfo: PipelineRunInfo;
  showBuildCaseFor: Stream<string>;
}

export class BuildCauseWidget extends Dropdown<Attrs> {
  toggleDropdown(vnode: m.Vnode<DropdownAttrs & Attrs>, e: MouseEvent) {
    super.toggleDropdown(vnode, e);
    if (vnode.attrs.show()) {
      vnode.attrs.showBuildCaseFor(vnode.attrs.pipelineRunInfo.counterOrLabel());
    } else {
      vnode.attrs.showBuildCaseFor("");
    }
  }

  protected doRenderButton(vnode: m.Vnode<DropdownAttrs & Attrs>): m.Children {
    if (!vnode.attrs.pipelineRunInfo.revision()) {
      return;
    }

    return <a class={styles.buildCauseButton}
              data-test-id="trigger-with-changes-button"
              onclick={this.toggleDropdown.bind(this, vnode)}>
      {vnode.attrs.pipelineRunInfo.buildCauseBy()}
    </a>;
  }

  protected doRenderDropdownContent(vnode: m.Vnode<DropdownAttrs & Attrs>) {
    if (!vnode.attrs.show()) {
      return;
    }

    return <div class={styles.buildDetails} data-test-id="build-details">
      <span class={styles.closeButtonWrapper}>
        <Icons.Close data-test-id="build-details-close-btn" onclick={this.toggleDropdown.bind(this, vnode)}/>
      </span>
      {vnode.attrs.pipelineRunInfo.materialRevisions().map((rev, index) => {
        return <MaterialRevisionWidget data-test-id={`material-revision-${index}`}
                                       materialRevision={rev}
                                       show={vnode.attrs.show}/>;
      })}
    </div>;
  }
}

interface MaterialRevisionAttrs {
  materialRevision: MaterialRevision;
  show: Stream<boolean>;
}

class MaterialRevisionWidget extends MithrilViewComponent<MaterialRevisionAttrs> {
  view(vnode: m.Vnode<MaterialRevisionAttrs, this>): m.Children {
    const materialRevision = vnode.attrs.materialRevision;
    return <div class={styles.materialRevisionDropdownContent}>
      <div class={classnames(styles.materialHeader, {[styles.changed]: materialRevision.changed()})}
           data-test-id="material-header">
        {materialRevision.scmType()} - {materialRevision.location()}
      </div>
      <div class={classnames(styles.modifications, {[styles.changed]: materialRevision.changed()})}
           data-test-id={`revisions-${materialRevision.revision()}`}>
        {materialRevision.modifications().map((modification) => {
          return <ModificationWidget modification={modification}/>;
        })}
      </div>
    </div>;
  }
}

interface ModificationAttrs {
  modification: Modification;
}

class ModificationWidget extends MithrilViewComponent<ModificationAttrs> {
  view(vnode: m.Vnode<ModificationAttrs, this>): m.Children {
    const modification = vnode.attrs.modification;
    return <div class={styles.modification} data-test-id={`modification-${modification.revision()}`}>
      <span class={styles.user} data-test-id="user">{modification.user()}</span>
      <span class={styles.comment} data-test-id="comment">{m.trust(modification.comment())}</span>
      <span class={styles.revision} data-test-id="revision">{modification.revision()}</span>
    </div>;
  }
}
