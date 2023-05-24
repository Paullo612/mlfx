# v.0.6.1

## Changes

* Fix compile time exception when fx:include resources attribute specified.
* Fix compilation and build of the project on Windows.
* Use Google's auto service to generate service descriptor files.
* Reduce runtime dependency set.

---

# v.0.6.0

## Changes

* Fix handling of child properties with string values.
* Simplify `@` prefixed attribute values handling.
* Simplify characters handling logic.
* Fix incorrect handling of primitives that take two variable slots.
* Simplify `CompiledFXMLLoader`'s `getURI` method logic.

## Breaking changes

* Inject fxml file URL to controllers instead of compiled class URL.

---

# v0.5.0

Initial release.