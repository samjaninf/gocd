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
import classnames from "classnames";
import {docsUrl} from "gen/gocd_version";
import {RestyleAttrs, RestyleViewComponent} from "jsx/mithril-component";
import _ from "lodash";
import m from "mithril";
import {TriStateCheckbox} from "models/tri_state_checkbox";
import s from "underscore.string";
import {v4 as uuid} from 'uuid';
import * as Buttons from "views/components/buttons";
import {OnClickHandler} from "views/components/buttons";
import {EncryptedValue} from "views/components/forms/encrypted_value";
import {Link} from "views/components/link";
import {SwitchBtn} from "views/components/switch";
import defaultStyles from "./forms.scss";

type Styles = typeof defaultStyles;

export interface RequiredFieldAttr {
  required?: boolean;
  hideRequiredAsterix?: boolean;
}

interface LabelAttr extends RequiredFieldAttr {
  label?: m.Children;
}

interface ErrorTextAttr {
  errorText?: string;
}

interface HelpTextAttr {
  helpText?: m.Children;
  docLink?: string;
}

interface PlaceholderAttr {
  placeholder?: string;
}

export interface DataTestIdAttr {
  dataTestId?: string;
}

interface ReadonlyAttr {
  readonly?: boolean;
}

interface SmallSizeAttr {
  small?: boolean;
}

export enum Size {
  SMALL, MEDIUM, MATCH_PARENT
}

class SizeTransformer {
  static transform(size?: Size, css: Styles = defaultStyles) {
    switch (size) {
      case Size.SMALL:
        return css.inputSmall;
      case Size.MEDIUM:
        return css.inputMedium;
      case Size.MATCH_PARENT:
        return css.inputMatchParent;
      default:
        return css.inputSmall;
    }
  }
}

interface SizeAttr {
  size?: Size;
}

interface BindingsAttr<T> {
  onchange?: (evt: any) => void;
  property: (newValue?: T) => T | undefined;
}

// tslint:disable-next-line:no-empty-interface
export interface BaseAttrs<T> extends DataTestIdAttr, HelpTextAttr, ErrorTextAttr, LabelAttr, BindingsAttr<T>, ReadonlyAttr, RestyleAttrs<Styles> {
}

interface DefaultAttrs {
  [key: string]: string | boolean;
}

const textInputFieldDefaultAttrs = {
  autocomplete:   "off",
  autocapitalize: "off",
  autocorrect:    "off",
  spellcheck:     false
};

interface FormResetButtonAttrs {
  onclick?: (e: MouseEvent) => void;
}

export interface Option {
  id: string;
  text: string;
  disabled?: boolean;
}

interface SelectFieldAttrs extends RestyleAttrs<Styles> {
  items: Array<Option | string>;
  selected?: string;
}

type HelpTextComponentAttrs = HelpTextAttr & { helpTextId: string };
type ErrorTextComponentAttrs = ErrorTextAttr & { errorId: string };
type LabelComponentAttrs = LabelAttr & { fieldId: string };

class RequiredLabel extends RestyleViewComponent<Styles, RequiredFieldAttr & RestyleAttrs<Styles>> {
  css = defaultStyles;

  static isRequiredField(attrs: RequiredFieldAttr) {
    return attrs.required;
  }

  static defaultAttributes(attrs: RequiredFieldAttr) {
    if (this.isRequiredField(attrs)) {
      return {
        "aria-required": true,
        "required":      true
      };
    }
  }

  view(vnode: m.Vnode<RequiredFieldAttr>) {
    if (RequiredLabel.isRequiredField(vnode.attrs) && !vnode.attrs.hideRequiredAsterix) {
      return (<span class={this.css.formLabelRequired}>*</span>);
    }
  }

}

function isMithrilVnode(o: any): o is m.Vnode {
  return !!("tag" in o && "children" in o);
}

function textFromVnode(vnode: m.Vnode): string {
  if (typeof vnode.children === "string") return vnode.children as string;
  if (vnode.children instanceof Array && vnode.children.length === 1) {
    return textFromVnode(vnode.children[0] as m.Vnode);
  }
  return "";
}

export function labelToId(label: string | number | boolean | m.Vnode | m.Children): string {
  if (_.isNil(label)) {
    return "";
  }

  if (typeof label === "string" || typeof label === "number" || label === true || label === false) {
    return s.slugify(label.toString());
  }

  if (isMithrilVnode(label)) {
    const temp = document.createElement("div");
    m.render(temp, label);
    const result = temp.innerText;
    m.render(temp, null);
    return s.slugify(result);
  }

  if (!(label instanceof Array)) {
    return "";
  }

   // We have an array. Let's get the text elements best guess, only consider top-level and don't recurse
  let text = "";
  for (const child of label) {
    if (typeof child === "string") {
      text += child;
    } else if (_.isNil(child)) {
      // ignore
    } else if (!(child instanceof Array)) {
      text += textFromVnode(child as m.Vnode);
    }
  }

  return s.slugify(text);
}

class Label extends RestyleViewComponent<Styles, LabelComponentAttrs & RestyleAttrs<Styles>> {
  css = defaultStyles;

  static hasLabelText(attrs: LabelAttr) {
    return !_.isEmpty(attrs.label);
  }

  static defaultAttributes(attrs: LabelAttr) {
    if (this.hasLabelText(attrs)) {
      return {
        "aria-label": labelToId(attrs.label),
      };
    }
  }

  view(vnode: m.Vnode<LabelComponentAttrs>) {
    if (Label.hasLabelText(vnode.attrs)) {
      return <label for={vnode.attrs.fieldId}
                    data-test-id={`form-field-label-${labelToId(vnode.attrs.label)}`}
                    class={classnames(this.css.formLabel)}>
        {vnode.attrs.label}
        <RequiredLabel {...vnode.attrs} />
        {vnode.children}
      </label>;
    }
  }

}

export class HelpText extends RestyleViewComponent<Styles, HelpTextComponentAttrs & RestyleAttrs<Styles>> {
  css = defaultStyles;

  static hasHelpText(attrs: HelpTextComponentAttrs) {
    return !_.isEmpty(attrs.helpText);
  }

  static defaultAttributes(attrs: HelpTextComponentAttrs) {
    if (HelpText.hasHelpText(attrs)) {
      return {"aria-describedby": attrs.helpTextId};
    }
  }

  static maybeDocLink(docLink?: string) {
    if (!_.isEmpty(docLink)) {
      return <span>&nbsp;<Link href={docsUrl(docLink)} target="_blank" externalLinkIcon={true}>Learn More</Link></span>;
    }
  }

  view(vnode: m.Vnode<HelpTextComponentAttrs>) {
    if (!_.isEmpty(vnode.attrs.helpText)) {
      return (<span id={vnode.attrs.helpTextId} class={classnames(this.css.formHelp)}>
        {vnode.attrs.helpText}{HelpText.maybeDocLink(vnode.attrs.docLink)}</span>);
    }
  }

}

export class ErrorText extends RestyleViewComponent<Styles, ErrorTextComponentAttrs & RestyleAttrs<Styles>> {
  css = defaultStyles;

  static hasErrorText(attrs: ErrorTextAttr) {
    return !_.isEmpty(attrs.errorText);
  }

  static defaultAttributes(attrs: ErrorTextComponentAttrs) {
    if (ErrorText.hasErrorText(attrs)) {
      return {"aria-errormessage": attrs.errorId};
    }
  }

  view(vnode: m.Vnode<ErrorTextComponentAttrs>) {
    if (ErrorText.hasErrorText(vnode.attrs)) {
      return <span class={this.css.formErrorText} id={vnode.attrs.errorId}>{vnode.attrs.errorText}</span>;
    }
  }
}

class FormResetButton extends RestyleViewComponent<Styles, FormResetButtonAttrs & ReadonlyAttr & RestyleAttrs<Styles>> {
  css = defaultStyles;

  view(vnode: m.Vnode<FormResetButtonAttrs & ReadonlyAttr>): any {
    return <div class={this.css.formInputReset}>
      <Buttons.Reset disabled={vnode.attrs.readonly} small={true} onclick={vnode.attrs.onclick}>{vnode.children}</Buttons.Reset>
    </div>;
  }
}

type EventName = "oninput" | "onclick";

function bindingAttributes<T>(attrs: BindingsAttr<T> & ReadonlyAttr,
                              eventName: EventName,
                              propertyAttribute: string) {
  const bindingAttributes: any = {
    [propertyAttribute]: attrs.property()
  };

  if (!attrs.readonly) {
    const existingHandler        = (attrs as any)[eventName];
    bindingAttributes[eventName] = (evt: any) => {
      if ("function" === typeof existingHandler) {
        existingHandler(evt);
      }

      attrs.property(evt.currentTarget[propertyAttribute]);
      if (attrs.onchange) {
        attrs.onchange(evt);
      }
    };
  }

  return bindingAttributes;
}

function defaultAttributes<T, V>(attrs: BaseAttrs<T> & V,
                                 id: string,
                                 helpTextId: string,
                                 errorId: string): DefaultAttrs {
  const required = RequiredLabel.isRequiredField(attrs as RequiredFieldAttr);

  const defaultAttrs: DefaultAttrs = {
    "readonly":     !!(attrs.readonly),
    "disabled":     !!(attrs.readonly),
    "required":     !!required,
    "id":           id,
    "data-test-id": `form-field-input-${labelToId(attrs.label)}`
  };

  if (attrs.dataTestId) {
    defaultAttrs["data-test-id"] = attrs.dataTestId as string;
  }

  if (attrs.title) {
    defaultAttrs.title = attrs.title as string;
  }

  return _.assign(defaultAttrs,
                  RequiredLabel.defaultAttributes(attrs),
                  Label.defaultAttributes(attrs),
                  HelpText.defaultAttributes({helpTextId, ...attrs}),
                  ErrorText.defaultAttributes({errorId, ...attrs})
  );
}

export abstract class FormField<T, V = {}> extends RestyleViewComponent<Styles, BaseAttrs<T> & V> {
  css = defaultStyles;

  protected readonly id: string         = `input-${uuid()}`;
  protected readonly helpTextId: string = `${this.id}-help-text`;
  protected readonly errorId: string    = `${this.id}-error-text`;

  view(vnode: m.Vnode<BaseAttrs<T> & V>) {
    return (
      <div class={classnames(this.css.formGroup,
                             {
                               [this.css.formHasError]: ErrorText.hasErrorText(vnode.attrs),
                               [this.css.formDisabled]: vnode.attrs.readonly
                             })}>
        {[<Label {...vnode.attrs} fieldId={this.id}/>]}
        {this.renderInputField(vnode)}
        {[<ErrorText {...vnode.attrs} errorId={this.errorId}/>]}
        {[<HelpText {...vnode.attrs} helpTextId={this.helpTextId}/>]}
      </div>
    );
  }

  protected defaultAttributes(attrs: BaseAttrs<T> & V): DefaultAttrs {
    return defaultAttributes(attrs, this.id, this.helpTextId, this.errorId);
  }

  protected bindingAttributes(attrs: BaseAttrs<T>,
                              eventName: EventName,
                              propertyAttribute: string): any {
    return bindingAttributes(attrs, eventName, propertyAttribute);
  }

  protected abstract renderInputField(vnode: m.Vnode<BaseAttrs<T> & V>): m.Children;
}

export interface TextFieldAttrs extends BaseAttrs<string>, RequiredFieldAttr, PlaceholderAttr {
  type?: string;
}

export type NumberFieldAttrs = BaseAttrs<number> & RequiredFieldAttr & PlaceholderAttr & { min?: string };

export class TextField extends FormField<string, RequiredFieldAttr & PlaceholderAttr> {
  renderInputField(vnode: m.Vnode<TextFieldAttrs>) {
    const baseAttrs: { [key: string]: string } = {
      type:  vnode.attrs.type || "text",
      class: this.css.formControl
    };

    return <input
      {...baseAttrs}
      {...this.defaultAttributes(vnode.attrs)}
      {...this.bindingAttributes(vnode.attrs, "oninput", "value")}
    />;
  }

  protected defaultAttributes(attrs: TextFieldAttrs) {
    const defaultAttributes = super.defaultAttributes(attrs);
    if (!_.isEmpty(attrs.placeholder)) {
      defaultAttributes.placeholder = attrs.placeholder as string;
    }

    return _.assign(defaultAttributes, textInputFieldDefaultAttrs);
  }
}

export class NumberField extends FormField<number, RequiredFieldAttr & PlaceholderAttr> {

  renderInputField(vnode: m.Vnode<NumberFieldAttrs>) {
    return (
      <input type="number"
             className={classnames(this.css.formControl)}
             {...this.defaultAttributes(vnode.attrs)}
             {...this.bindingAttributes(vnode.attrs, "oninput", "value")}
      />
    );
  }

  protected defaultAttributes(attrs: NumberFieldAttrs) {
    const defaultAttributes = super.defaultAttributes(attrs);
    if (!_.isEmpty(attrs.placeholder)) {
      defaultAttributes.placeholder = attrs.placeholder as string;
    }
    if (!_.isEmpty(attrs.min)) {
      defaultAttributes.min = attrs.min as string;
    }
    return _.assign(defaultAttributes, textInputFieldDefaultAttrs);
  }
}

interface ResizableAttrs {
  resizable?: boolean;
}

interface InitialTextAreaSizeAttrs {
  rows?: number;
  size?: Size;
}

type TextAreaFieldAttrs =
  BaseAttrs<string>
  & RequiredFieldAttr
  & PlaceholderAttr
  & ResizableAttrs
  & InitialTextAreaSizeAttrs;

export class TextAreaField extends FormField<string, TextAreaFieldAttrs> {
  renderInputField(vnode: m.Vnode<TextAreaFieldAttrs>) {

    return (
      <textarea
        class={classnames(this.css.formControl,
                          this.css.textArea,
                          SizeTransformer.transform(vnode.attrs.size, this.css),
                          {[this.css.textareaFixed]: !(vnode.attrs.resizable)})}
        {...this.defaultAttributes(vnode.attrs)}
        rows={vnode.attrs.rows}
        oninput={(e: any) => {
          vnode.attrs.property((e.target as HTMLTextAreaElement).value);
          if (vnode.attrs.onchange) {
            vnode.attrs.onchange(e);
          }
        }}
        value={vnode.attrs.property()}/>
    );
  }

  protected defaultAttributes(attrs: BaseAttrs<string> & RequiredFieldAttr & PlaceholderAttr) {
    const defaultAttributes = super.defaultAttributes(attrs);
    if (!_.isEmpty(attrs.placeholder)) {
      defaultAttributes.placeholder = attrs.placeholder as string;
    }

    return _.assign(defaultAttributes, textInputFieldDefaultAttrs);
  }

}

export class PasswordField extends FormField<EncryptedValue, RequiredFieldAttr & PlaceholderAttr> {
  inputType = "password";

  renderInputField(vnode: m.Vnode<BaseAttrs<EncryptedValue> & RequiredFieldAttr & PlaceholderAttr>) {
    const defaultAttributes = this.defaultAttributes(vnode.attrs);
    const showHidePassword  = vnode.attrs.property()!.isEditing() && !vnode.attrs.readonly
      ? <span id="wrapper-input" className={classnames(this.inputType === "password" ? this.css.showPassword : this.css.hidePassword)} onclick={this.onClickOfShowHideIcon.bind(this, defaultAttributes.id)}/>
      : undefined;
    const input             = [
      <input type={this.inputType}
             className={classnames(this.css.formControl, this.css.inline)}
             {...defaultAttributes}
             {...this.bindingAttributes(vnode.attrs, "oninput", "value")}/>,
      showHidePassword
    ];

    const callbackOnReset = () => {
      this.inputType = "password";
    };
    const callbackOnChange = () => {
      document.getElementById(defaultAttributes.id)?.focus();
    };
    return [input, PasswordField.resetOrChange(vnode, callbackOnReset, callbackOnChange)];
  }

  protected defaultAttributes(attrs: BaseAttrs<EncryptedValue> & RequiredFieldAttr & PlaceholderAttr): any {
    return _.assign(super.defaultAttributes(attrs), textInputFieldDefaultAttrs, {
      readonly: !attrs.property()!.isEditing()
    }, _.isEmpty(attrs.placeholder) ? {} : {placeholder: attrs.placeholder});
  }

  protected bindingAttributes(attrs: BaseAttrs<EncryptedValue>,
                              eventName: string,
                              propertyAttribute: string): any {
    if (attrs.property()!.isEditing()) {
      return {
        [eventName]:         (evt: any) => {
          if (attrs.onchange) {
            attrs.onchange(evt);
          }

          return attrs.property()!.value(evt.currentTarget.value);
        },
        [propertyAttribute]: attrs.property()!.value()
      };
    } else {
      return {
        value: attrs.property()!.valueForDisplay()
      };
    }

  }

  private static resetOrChange(vnode: m.Vnode<BaseAttrs<EncryptedValue> & RequiredFieldAttr & PlaceholderAttr>, callbackOnReset: () => any, callbackOnChange: () => any) {
    if (vnode.attrs.property()!.isEditing()) {
      return <FormResetButton css={vnode.attrs.css}
                              readonly={vnode.attrs.readonly}
                              data-test-id="reset-input"
                              onclick={() => {
                                vnode.attrs.property()!.resetToOriginal.call(vnode.attrs.property());
                                callbackOnReset();
                              }}>Reset</FormResetButton>;
    } else {
      return <FormResetButton css={vnode.attrs.css}
                              readonly={vnode.attrs.readonly}
                              data-test-id="change-input"
                              onclick={() => {
                                vnode.attrs.property()!.edit.call(vnode.attrs.property());
                                callbackOnChange();
                              }}>Change</FormResetButton>;
    }
  }

  private onClickOfShowHideIcon(id: string, e: MouseEvent) {
    e.stopPropagation();
    this.inputType = this.inputType === "password" ? "text" : "password";
  }
}

export class SearchField extends FormField<string, PlaceholderAttr> {
  view(vnode: m.Vnode<BindingsAttr<string> & PlaceholderAttr>): any {
    return (
      <span class={classnames(this.css.searchBoxWrapper)}>
      <input type="search"
             class={classnames(this.css.formControl, this.css.searchBoxInput)}
             placeholder={vnode.attrs.placeholder}
             {...this.defaultAttributes(vnode.attrs)}
             {...this.bindingAttributes(vnode.attrs, "oninput", "value")}/>
      </span>
    );
  }

  protected renderInputField(vnode: m.Vnode<BaseAttrs<string> & PlaceholderAttr>): m.Children {
    throw new Error("unsupported!");
  }
}

export class CheckboxField extends FormField<boolean> {
  view(vnode: m.Vnode<BaseAttrs<boolean>>) {
    return (
      <div class={classnames(this.css.formGroup, {
        [this.css.formHasError]: ErrorText.hasErrorText(vnode.attrs as ErrorTextAttr),
        [this.css.formDisabled]: vnode.attrs.readonly
      })}>
        <div class={this.css.formCheck}>
          {this.renderInputField(vnode)}
          <Label {...vnode.attrs as LabelAttr} fieldId={this.id}/>
          <ErrorText {...vnode.attrs as ErrorTextAttr} errorId={this.errorId}/>
          <HelpText {...vnode.attrs as HelpTextAttr} helpTextId={this.helpTextId}/>
        </div>
      </div>
    );
  }

  renderInputField(vnode: m.Vnode<BaseAttrs<boolean>>) {
    return (
      <input type="checkbox"
             {...this.defaultAttributes(vnode.attrs)}
             {...this.bindingAttributes(vnode.attrs, "onclick", "checked")}
             class={classnames(this.css.formCheckInput)}/>);
  }
}

export class TriStateCheckboxField extends FormField<TriStateCheckbox> {
  view(vnode: m.Vnode<BaseAttrs<TriStateCheckbox>>) {
    return (
      <div class={classnames(this.css.formGroup, {
        [this.css.formHasError]: ErrorText.hasErrorText(vnode.attrs as ErrorTextAttr),
        [this.css.formDisabled]: vnode.attrs.readonly
      })}>
        <div class={this.css.formCheck}>
          {this.renderInputField(vnode)}
          <Label {...vnode.attrs as LabelAttr} fieldId={this.id}/>
          <ErrorText {...vnode.attrs as ErrorTextAttr} errorId={this.errorId}/>
          <HelpText {...vnode.attrs as HelpTextAttr} helpTextId={this.helpTextId}/>
        </div>
      </div>
    );
  }

  renderInputField(vnode: m.Vnode<BaseAttrs<TriStateCheckbox>>) {
    return (
      <input type="checkbox"
             {...this.defaultAttributes(vnode.attrs)}
             {...this.bindingAttributes(vnode.attrs, "onclick", "checked")}
             class={classnames(this.css.formCheckInput)}/>);
  }

  protected bindingAttributes(attrs: BaseAttrs<TriStateCheckbox>,
                              eventName: string,
                              propertyAttribute: string): any {
    const triStateCheckbox = attrs.property()!;

    const bindingAttributes: any = {
      [propertyAttribute]: triStateCheckbox.isChecked(),
      indeterminate:       triStateCheckbox.isIndeterminate()
    };

    if (!attrs.readonly) {
      bindingAttributes[eventName] = (evt: any) => {
        triStateCheckbox.click();
        if (attrs.onchange) {
          attrs.onchange(evt);
        }
      };
    }

    return bindingAttributes;
  }
}

interface RadioData<T> {
  label: m.Children;
  value: T;
  helpText?: string;
  tooltip?: m.Child;
}

export interface RadioButtonAttrs<T> extends RestyleAttrs<Styles> {
  label?: m.Children;
  errorText?: string;
  readonly?: boolean;
  required?: boolean;
  inline?: boolean;
  property: (newValue?: T) => T;
  possibleValues: Array<RadioData<T>>;
  onchange?: (newValue: T) => void;
  dataTestId?: string;
}

export class RadioField<T extends string = string> extends RestyleViewComponent<Styles, RadioButtonAttrs<T>> {
  css = defaultStyles;

  protected readonly id: string = `input-${uuid()}`;

  view(vnode: m.Vnode<RadioButtonAttrs<T>>) {
    const classes = classnames(this.css.formGroup, {[this.css.formHasError]: this.hasErrorText(vnode)});

    return <li class={classes} data-test-id={vnode.attrs.dataTestId}>
      {this.label(vnode.attrs)}
      <div class={this.inlined(vnode.attrs.inline)}>
        {this.renderInputField(vnode)}
      </div>
      <ErrorText {...vnode.attrs} errorId={`${this.id}-error-text`}/>
    </li>;
  }

  private inlined(flag?: boolean) {
    if (flag) {
      return this.css.inlineRadioBtns;
    }
  }

  private label({label, required}: RadioButtonAttrs<T>) {
    if (label) {
      return <label for={this.id} class={this.css.formLabel} data-test-id="form-field-label">
        {label}{this.requiredMarker(required)}
      </label>;
    }
  }

  private requiredMarker(required?: boolean) {
    if (required) {
      return <span class={this.css.formLabelRequired}>*</span>;
    }
  }

  private hasErrorText(vnode: m.Vnode<RadioButtonAttrs<T>>) {
    return !_.isEmpty(vnode.attrs.errorText);
  }

  private renderInputField(vnode: m.Vnode<RadioButtonAttrs<T>>) {
    const result: m.Children[] = [];

    vnode.attrs.possibleValues.forEach((radioData) => {
      const radioButtonId = `${this.id}-${s.slugify(radioData.value)}`;
      result.push(
        <li data-test-id={`input-field-for-${radioData.value}`} className={this.css.radioField}>
          <input type="radio"
                 id={radioButtonId}
                 {...defaultAttributes(vnode.attrs,
                                       radioButtonId,
                                       `${radioButtonId}-help-text`,
                                       `${radioButtonId}-error-id`)}
                 checked={radioData.value === vnode.attrs.property()}
                 data-test-id={`radio-${s.slugify(radioData.value)}`}
                 name={this.id}
                 onchange={() => {
                   vnode.attrs.property(radioData.value);
                   if (vnode.attrs.onchange) {
                     vnode.attrs.onchange(radioData.value);
                   }
                 }}/>
          <label for={radioButtonId} className={this.css.radioLabel}
                 data-test-id="form-field-label">{radioData.label}</label>
          {radioData?.tooltip}
          <HelpText helpText={radioData.helpText} helpTextId={`help-text-${radioButtonId}`} css={this.css}/>
        </li>
      );
    });

    return result;
  }
}

export class Switch extends FormField<boolean, SmallSizeAttr> {
  protected renderInputField(vnode: m.Vnode<BaseAttrs<boolean> & SmallSizeAttr>): m.Children {
    return <SwitchBtn small={vnode.attrs.small} field={vnode.attrs.property}/>;
  }
}

export class SelectField extends FormField<string, RequiredFieldAttr> {
  renderInputField(vnode: m.Vnode<BaseAttrs<string> & RequiredFieldAttr>) {
    return (
      <select
        class={this.css.formControl}
        {...this.defaultAttributes(vnode.attrs)}
        onchange={(e: any) => {
          vnode.attrs.property((e.target as HTMLInputElement).value);
          if (vnode.attrs.onchange) {
            vnode.attrs.onchange(e);
          }
        }}
        value={vnode.attrs.property()}>
        {vnode.children}
      </select>
    );
  }
}

export class SelectFieldOptions extends RestyleViewComponent<Styles, SelectFieldAttrs> {
  css = defaultStyles;

  view(vnode: m.Vnode<SelectFieldAttrs>): m.Children {
    return _.map(vnode.attrs.items, (optionOrString: Option | string) => {
      let id: string;
      let text: string;
      let disabled = false;
      if (typeof optionOrString === "string") {
        id   = optionOrString as string;
        text = optionOrString as string;
      } else {
        id       = (optionOrString as Option).id;
        text     = (optionOrString as Option).text;
        disabled = (optionOrString as Option).disabled || disabled;
      }

      return <option key={id}
                     value={id}
                     disabled={disabled}
                     selected={vnode.attrs.selected === id}>{text}</option>;
    });
  }
}

interface ButtonDisableReason {
  buttonDisableReason: string;
}

interface ButtonName {
  name?: string;
}

// tslint:disable-next-line:no-empty-interface
interface TextFieldWithButtonAttrs extends PlaceholderAttr, OnClickHandler, BindingsAttr<string>, ReadonlyAttr, DataTestIdAttr, ButtonDisableReason, ButtonName, SizeAttr, RestyleAttrs<Styles> {
}

abstract class TextFieldWithButton extends RestyleViewComponent<Styles, TextFieldWithButtonAttrs> {
  css = defaultStyles;

  protected readonly id: string         = `input-${uuid()}`;
  protected readonly helpTextId: string = `${this.id}-help-text`;
  protected readonly errorId: string    = `${this.id}-error-text`;

  view(vnode: m.Vnode<TextFieldWithButtonAttrs>) {

    const defaultAttrs: DefaultAttrs = {};

    if (vnode.attrs.dataTestId) {
      defaultAttrs["data-test-id"] = vnode.attrs.dataTestId;
    }

    return (
      <div class={classnames(this.css.formGroup,
                             this.css.formGroupTextFieldWithButton,
                             {[this.css.formDisabled]: vnode.attrs.readonly})} {...defaultAttrs}>
        {this.renderInputField(vnode)}
        {this.renderButton(vnode)}
      </div>
    );
  }

  protected defaultAttributes(attrs: TextFieldWithButtonAttrs): DefaultAttrs {
    const result = defaultAttributes(attrs, this.id, this.helpTextId, this.errorId);
    if (!_.isEmpty(attrs.placeholder)) {
      result.placeholder = attrs.placeholder as string;
    }

    delete result["data-test-id"];

    return _.assign(result, textInputFieldDefaultAttrs);
  }

  protected renderButton(vnode: m.Vnode<TextFieldWithButtonAttrs>): m.Children {
    const btnAttrs = this.btnAttrs(vnode);
    return <button onclick={this.onButtonClick(vnode)}
                   class={classnames(this.css.quickAddButton)} {...btnAttrs}>
      {this.name()}
    </button>;
  }

  protected renderInputField(vnode: m.Vnode<TextFieldWithButtonAttrs>): m.Children {
    const inputSizeClass = SizeTransformer.transform(vnode.attrs.size, this.css);
    return <input type="text"
                  class={classnames(this.css.formControl, inputSizeClass)}
                  {...this.defaultAttributes(vnode.attrs)}
                  {...bindingAttributes(vnode.attrs, "oninput", "value")}/>;
  }

  protected btnAttrs(vnode: m.Vnode<TextFieldWithButtonAttrs>): DefaultAttrs {
    const btnAttrs: DefaultAttrs = {};

    if (_.isEmpty(vnode.attrs.property())) {
      btnAttrs.disabled = true;
      btnAttrs.title    = vnode.attrs.buttonDisableReason;
    }
    return btnAttrs;
  }

  protected onButtonClick(vnode: m.Vnode<TextFieldWithButtonAttrs>) {
    return vnode.attrs.onclick;
  }

  protected abstract name(): m.Child;
}

export class QuickAddField extends TextFieldWithButton {
  protected name(): m.Child {
    return "Add";
  }
}

export class CopyField extends TextFieldWithButton {
  protected renderInputField(vnode: m.Vnode<TextFieldWithButtonAttrs>): m.Children {
    vnode.attrs.readonly = true;
    return super.renderInputField(vnode);
  }

  protected defaultAttributes(attrs: TextFieldWithButtonAttrs): DefaultAttrs {
    const defaultAttrs = super.defaultAttributes(attrs);
    delete defaultAttrs.disabled;
    return defaultAttrs;
  }

  protected name(): m.Child {
    return "Copy";
  }

  protected onButtonClick(vnode: m.Vnode<TextFieldWithButtonAttrs>) {
    return async () => {
      await navigator.clipboard.writeText(vnode.attrs.property()!);
    };
  }
}

export class SearchFieldWithButton extends QuickAddField {
  protected name(): m.Child {
    return "Search";
  }

  protected renderInputField(vnode: m.Vnode<TextFieldWithButtonAttrs>) {
    return <span class={this.css.searchBoxWrapper}>
      <input type="search"
             class={classnames(this.css.formControl, this.css.searchBoxInput)}
             {...this.defaultAttributes(vnode.attrs)}
             {...bindingAttributes(vnode.attrs, "oninput", "value")}/>
    </span>;
  }
}
