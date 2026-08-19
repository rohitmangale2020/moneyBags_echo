using System;
using System.IO.Compression;

public static class UnzipCompat
{
    public static int Main(string[] args)
    {
        try
        {
            if (args.Length == 2 && args[0] == "-Z1")
            {
                using (var archive = ZipFile.OpenRead(args[1]))
                foreach (var entry in archive.Entries) Console.WriteLine(entry.FullName);
                return 0;
            }
            if (args.Length == 3 && args[0] == "-p")
            {
                using (var archive = ZipFile.OpenRead(args[1]))
                using (var input = archive.GetEntry(args[2]).Open())
                using (var output = Console.OpenStandardOutput()) input.CopyTo(output);
                return 0;
            }
            return 1;
        }
        catch { return 1; }
    }
}
