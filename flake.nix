{
	description = "CNDL_chat+ development shell";

	inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

	outputs = { nixpkgs, ... }:
		let
			systems = [ "x86_64-linux" "aarch64-linux" ];
		in {
			devShells = nixpkgs.lib.genAttrs systems (system:
				let
					pkgs = import nixpkgs { inherit system; };
				in {
					default = pkgs.mkShell {
						packages = with pkgs; [ jdk25 jdk21 git ];
						JAVA_HOME = "${pkgs.jdk25}";
					};
				});
		};
}
