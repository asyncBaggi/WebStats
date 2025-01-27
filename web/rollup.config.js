import typescript from "@rollup/plugin-typescript"

export default {
	input: "src/WebStats.ts",
	output: {
		file: "WebStats-dist.js",
		sourcemap: true,
		banner: `\
/*
	WebStats version 1.7
	https://github.com/Dantevg/WebStats
	
	by RedPolygon
	
	Licence: MIT
*/
`,
	},
	treeshake: false,
	plugins: [typescript()],
}
