from PIL import Image
import os

# Source logo
logo_path = r"d:\Coding\Projects\VibeCoded\pixelPull\logo.png"

# Android icon sizes for different densities
sizes = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192
}

# Base directory
base_dir = r"d:\Coding\Projects\VibeCoded\pixelPull\app\src\main\res"

# Open the source image
img = Image.open(logo_path)

# Generate icons for each density
for density, size in sizes.items():
    # Resize image
    resized = img.resize((size, size), Image.Resampling.LANCZOS)
    
    # Create output paths
    output_dir = os.path.join(base_dir, f'mipmap-{density}')
    
    # Save both square and round versions
    output_path = os.path.join(output_dir, 'ic_launcher.png')
    resized.save(output_path, 'PNG')
    print(f'Created {output_path}')
    
    output_path_round = os.path.join(output_dir, 'ic_launcher_round.png')
    resized.save(output_path_round, 'PNG')
    print(f'Created {output_path_round}')

print('All icons generated successfully!')
