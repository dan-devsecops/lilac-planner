terraform {
  required_version = ">= 1.6.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    azuread = {
      source  = "hashicorp/azuread"
      version = "~> 3.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Remote state. Create the storage account/container once (see README),
  # then uncomment and run `terraform init`.
  # backend "azurerm" {
  #   resource_group_name  = "tfstate-rg"
  #   storage_account_name = "lilactfstate"
  #   container_name       = "tfstate"
  #   key                  = "lilac-planner.tfstate"
  # }
}

provider "azurerm" {
  features {}
}

provider "azuread" {}
