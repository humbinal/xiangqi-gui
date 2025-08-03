package cmd

import (
	"fmt"
	"github.com/spf13/cobra"
	"humbinal.com/xiangqi-engine-server/server"
	"os"
)

var Pikafish string
var CpuFeature string

var rootCmd = &cobra.Command{
	Use:   "xq-engine",
	Short: "xq-engine is a websocket server for pikafish uci engine.",
	Run: func(cmd *cobra.Command, args []string) {
		fmt.Println("xq-engine start, pikafish:", Pikafish)
		server.StartServer(Pikafish, CpuFeature)
	},
}

func Execute() {
	rootCmd.Flags().StringVar(&Pikafish, "pikafish", "", "pikafish engine directory path.")
	rootCmd.Flags().StringVar(&CpuFeature, "cpu-feature", "bmi2", "cpu feature: vnni512 > avx512 > avx512f > avxvnni > bmi2 > avx2 > sse41-popcnt.")
	_ = rootCmd.MarkFlagRequired("pikafish")
	if err := rootCmd.Execute(); err != nil {
		os.Exit(1)
	}
}
