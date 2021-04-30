def call(Map givenConfig = [:]) {
  def defaultConfig = [
    /**
      * The Jenkins node, or label, that will be allocated for this build.
      */
    "credentialsId": "",
    /**
      * All config specific to NPM repo type.
      */
    "serverUrl": "https://api.okd-c1.eclipse.org:6443",
    "namespace": "",
    /**
     * Selector (label query) to filter on, supports '=', '==', and '!='.(e.g. key1=value1,key2=value2)
     */
    "selector=": "",
    /**
     * Kind of resource to search for
     */
    "kind": "deployment.v1.apps",
    "containerName": "",
    "newImageRef": "",
  ]
  def effectiveConfig = defaultConfig + givenConfig

  withKubeConfig([credentialsId: effectiveConfig.credentialsId, serverUrl: effectiveConfig.serverUrl]) {
    sh """
      resourcesJson="$(kubectl get "${effectiveConfig.kind}" -n "${effectiveConfig.namespace}" -l "${effectiveConfig.selector}" -o json)"
      if [[ $(jq -r '.items | length' <<<"\${resourcesJson}") -eq 0 ]]; then
        echo "ERROR: Unable to find a '${effectiveConfig.kind}' to patch matching selector '${effectiveConfig.selector}' in namespace '${effectiveConfig.namespace}'"
        exit 1
      else 
        firstResourceName="$(jq -r '.items[0].metadata.name' <<<"\${resourcesJson}")"
        kubectl set image "${effectiveConfig.kind}/\${firstResourceName}" -n "${effectiveConfig.namespace}" "${effectiveConfig.containerName}=${effectiveConfig.newImageRef}" --record=true
        if ! kubectl rollout status "${effectiveConfig.kind}/\${firstResourceName}" -n "${effectiveConfig.namespace}"; then
          # will fail if rollout does not succeed in less than .spec.progressDeadlineSeconds
          kubectl rollout undo "${effectiveConfig.kind}/\${firstResourceName}" -n "${effectiveConfig.namespace}"
          exit 1
        fi
      fi
    """
  }
}