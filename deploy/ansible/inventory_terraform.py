#!/usr/bin/env python3
"""Dynamic inventory
Usage: ansible-playbook -i inventory_terraform.py playbook.yml
or make it the default - ansible.cfg
"""
import json
import os
import subprocess
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
TF_DIR = os.path.join(SCRIPT_DIR, "..", "terraform", "digitalocean-droplet")
SSH_KEY = "~/.ssh/id_ed25519_ag_digitalocean_tf"
HOSTNAME = "tf-lilac-planner-prod-01"


def build_inventory():
    try:
        result = subprocess.run(
            ["terraform", f"-chdir={TF_DIR}", "output", "-json"],
            capture_output=True, text=True, check=True,
        )
    except (FileNotFoundError, subprocess.CalledProcessError) as e:
        print(f"inventory_terraform.py: failed to read terraform output: {e}", file=sys.stderr)
        sys.exit(1)

    outputs = json.loads(result.stdout)
    ip = outputs["droplet_ip"]["value"]

    return {
        "droplet": {"hosts": [HOSTNAME]},
        "_meta": {
            "hostvars": {
                HOSTNAME: {
                    "ansible_host": ip,
                    "ansible_user": "root",
                    "ansible_ssh_private_key_file": SSH_KEY,
                }
            }
        },
    }


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--host":
        print(json.dumps({}))  # hostvars already served via _meta above
    else:
        print(json.dumps(build_inventory()))
