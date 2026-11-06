# Managed Kubernetes cluster. Workload Identity + OIDC issuer are enabled so
# pods can later federate to Entra (e.g. Key Vault CSI / External Secrets)
# without static credentials.
resource "azurerm_kubernetes_cluster" "this" {
  name                = "${var.prefix}-aks"
  resource_group_name = azurerm_resource_group.this.name
  location            = var.location
  dns_prefix          = "${var.prefix}-aks"
  kubernetes_version  = var.kubernetes_version

  oidc_issuer_enabled       = true
  workload_identity_enabled = true

  # Container Insights - ships container/pod/node CPU+memory metrics and logs
  # to the Log Analytics workspace in monitoring.tf, and populates the AKS
  # resource's "Insights" blade in the portal with time-series dashboards.
  oms_agent {
    log_analytics_workspace_id = azurerm_log_analytics_workspace.this.id
  }

  default_node_pool {
    name           = "default"
    node_count     = var.node_count
    vm_size        = var.node_vm_size
    vnet_subnet_id = azurerm_subnet.aks.id

    # Declared explicitly, not left to the provider default: Azure's API
    # always returns this block (defaulting max_surge to "10%") whether or
    # not it's in config - omitting it here just means every `terraform
    # plan`/`apply` perpetually "wants" to null it back out, which Azure
    # never actually honors. Matching the real default stops the drift.
    upgrade_settings {
      max_surge = "10%"
    }
  }

  identity {
    type = "SystemAssigned"
  }

  network_profile {
    network_plugin = "azure"
    network_policy = "azure"
  }

  tags = var.tags
}
