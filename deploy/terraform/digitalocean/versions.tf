terraform {
  required_version = ">= 1.6.0"

  required_providers {
    digitalocean = {
      source  = "digitalocean/digitalocean"
      version = "~> 2.40"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Remote state via DO Spaces (uncomment after creating the bucket).
  # backend "s3" {
  #   bucket                      = "lilac-tfstate"
  #   key                         = "do/lilac-planner.tfstate"
  #   region                      = "us-east-1"             # placeholder; Spaces ignores this
  #   endpoint                    = "https://fra1.digitaloceanspaces.com"
  #   skip_credentials_validation = true
  #   skip_metadata_api_check     = true
  # }
}

provider "digitalocean" {
  token = var.do_token
}
